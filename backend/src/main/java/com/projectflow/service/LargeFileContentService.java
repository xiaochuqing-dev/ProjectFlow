package com.projectflow.service;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds a bounded, lexical content map. It is deliberately not a parser or a
 * factual source: precise code relationships still come from the Structure SPI.
 */
@Service
public class LargeFileContentService {
    private static final int PROBE_BYTES = 65_536;
    private static final int RANGE_LINES = 24;
    private static final int MAX_SECTIONS = 240;
    private static final int MAX_ANCHORS = 240;
    private static final int MAX_REPEATED = 40;
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern SYMBOL = Pattern.compile(
        "^\\s*(?:(?:public|private|protected|static|final|abstract|async|export|default|internal|sealed|open)\\s+)*"
            + "(?:class|interface|record|enum|def|fun|function|fn|func|sub|module|namespace)\\s+"
            + "([\\p{L}_$][\\p{L}\\p{N}_$.-]{0,120})"
    );
    private static final Pattern MARKER = Pattern.compile(
        "(?i)\\b(TODO|FIXME|deprecated|deprecation|superseded|replaced by|decision|decided|"
            + "conflict|contradict|current|obsolete|known limitation|technical debt)\\b|"
            + "(决定|决策|废弃|弃用|替代|冲突|矛盾|当前|过时|已知限制|技术债)"
    );

    private final SensitiveContentRedactor redactor;

    @Value("${projectflow.understanding.max-content-map-bytes:67108864}")
    private long maxContentMapBytes = 67_108_864;

    public LargeFileContentService(SensitiveContentRedactor redactor) {
        this.redactor = redactor;
    }

    public ContentMap analyze(Path target, int maxTotalChars) {
        if (target == null || !Files.isRegularFile(target)) return unavailable("文件不存在");
        try {
            long byteSize = Files.size(target);
            int outputBudget = Math.max(1_000, Math.min(64_000, maxTotalChars <= 0 ? 16_000 : maxTotalChars));
            byte[] probe = readProbe(target);
            Encoding encoding = detectEncoding(probe);
            if (encoding.binary()) {
                return new ContentMap(
                    encoding.name(), true, byteSize, 0, "", false,
                    List.of(), List.of(), List.of(), List.of(),
                    List.of("检测到二进制或含 NUL 的非文本内容")
                );
            }
            long readLimit = Math.max(1_048_576, maxContentMapBytes);
            boolean partial = byteSize > readLimit;
            String sourceHash = hash(target, readLimit, partial);
            Scan scan = scan(target, encoding.charset(), readLimit);
            List<ContentSection> sections = closeSections(scan.sections(), scan.lineCount());
            List<RangeRequest> requests = representativeRanges(scan, sections);
            List<RangeSample> samples = new ArrayList<>();
            int consumed = 0;
            Set<String> seenRanges = new LinkedHashSet<>();
            for (RangeRequest request : requests) {
                if (consumed >= outputBudget) break;
                RangeSample sample = readRangeInternal(
                    target, encoding.charset(), sourceHash, request.kind(), request.startLine(),
                    request.endLine(), Math.min(4_000, outputBudget - consumed), readLimit
                );
                String key = sample.startLine() + ":" + sample.endLine();
                if (sample.text().isBlank() || !seenRanges.add(key)) continue;
                samples.add(sample);
                consumed += sample.text().length();
            }
            List<String> limitations = new ArrayList<>();
            if (partial) {
                limitations.add("文件超过 Content Map 读取上限，未扫描部分保持 UNKNOWN");
            }
            if (!"UTF-8".equals(encoding.name()) && !encoding.name().startsWith("UTF-16")) {
                limitations.add("编码只能保守识别为 " + encoding.name());
            }
            List<String> unread = unreadRanges(scan.lineCount(), samples, partial, byteSize, readLimit);
            return new ContentMap(
                encoding.name(), false, byteSize, scan.lineCount(), sourceHash, partial,
                sections, List.copyOf(scan.anchors()), List.copyOf(samples), unread, List.copyOf(limitations)
            );
        } catch (IOException exception) {
            return unavailable("文件读取失败");
        }
    }

    public RangeSample readRange(Path target, long startLine, long endLine, String kind, int maxChars) {
        ContentMap map = analyze(target, 1_000);
        if (map.binary() || map.sourceHash().isBlank()) {
            return new RangeSample(normalizeKind(kind), 0, 0, 0, 0, "", true, "");
        }
        try {
            Encoding encoding = detectEncoding(readProbe(target));
            return readRangeInternal(
                target, encoding.charset(), map.sourceHash(), normalizeKind(kind),
                Math.max(1, startLine), Math.max(startLine, endLine),
                Math.max(256, maxChars), Math.max(1_048_576, maxContentMapBytes)
            );
        } catch (IOException exception) {
            return new RangeSample(normalizeKind(kind), 0, 0, 0, 0, map.sourceHash(), true, "");
        }
    }

    public RangeSample queryTargeted(Path target, String query, int radiusLines, int maxChars) {
        if (query == null || query.isBlank()) return readRange(target, 1, RANGE_LINES, "QUERY", maxChars);
        ContentMap map = analyze(target, 1_000);
        if (map.binary()) return new RangeSample("QUERY", 0, 0, 0, 0, "", true, "");
        try {
            Encoding encoding = detectEncoding(readProbe(target));
            String needle = query.toLowerCase(Locale.ROOT);
            long lineNumber = 0;
            try (BufferedReader reader = reader(target, encoding.charset())) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (line.toLowerCase(Locale.ROOT).contains(needle)) break;
                }
            }
            if (lineNumber == 0) return new RangeSample("QUERY", 0, 0, 0, 0, map.sourceHash(), true, "");
            int radius = Math.max(1, Math.min(100, radiusLines));
            return readRangeInternal(
                target, encoding.charset(), map.sourceHash(), "QUERY",
                Math.max(1, lineNumber - radius), lineNumber + radius,
                Math.max(256, maxChars), Math.max(1_048_576, maxContentMapBytes)
            );
        } catch (IOException exception) {
            return new RangeSample("QUERY", 0, 0, 0, 0, map.sourceHash(), true, "");
        }
    }

    public String toPromptText(ContentMap map, int maxChars) {
        if (map == null || map.binary()) return "";
        int limit = Math.max(500, maxChars);
        StringBuilder body = new StringBuilder();
        body.append("CONTENT_MAP encoding=").append(map.encoding())
            .append(" lines=").append(map.lineCount())
            .append(" bytes=").append(map.byteSize())
            .append(" hash=").append(map.sourceHash())
            .append(" partial=").append(map.partial()).append('\n');
        map.sections().stream().limit(20).forEach(section -> body.append("SECTION ")
            .append(section.type()).append(" lines ").append(section.startLine()).append('-')
            .append(section.endLine()).append(' ').append(section.title()).append('\n'));
        map.anchors().stream().limit(20).forEach(anchor -> body.append("ANCHOR ")
            .append(anchor.type()).append(" line ").append(anchor.line()).append(' ')
            .append(anchor.text()).append('\n'));
        for (RangeSample sample : map.samples()) {
            body.append("RANGE ").append(sample.kind()).append(" lines ")
                .append(sample.startLine()).append('-').append(sample.endLine())
                .append(" bytes ").append(sample.startByte()).append('-').append(sample.endByte())
                .append(" truncated=").append(sample.truncated()).append('\n')
                .append(sample.text()).append('\n');
        }
        StringBuilder disclosure = new StringBuilder();
        if (!map.unreadRanges().isEmpty()) {
            disclosure.append("UNREAD_RANGES ").append(String.join(";", map.unreadRanges())).append('\n');
        }
        if (!map.limitations().isEmpty()) {
            disclosure.append("LIMITATIONS ").append(String.join(";", map.limitations())).append('\n');
        }
        String safeBody = redactor.redact(body.toString());
        String safeDisclosure = redactor.redact(disclosure.toString());
        if (safeBody.length() + safeDisclosure.length() <= limit) return safeBody + safeDisclosure;
        int bodyLimit = Math.max(0, limit - safeDisclosure.length() - 1);
        String boundedBody = safeBody.substring(0, Math.min(bodyLimit, safeBody.length()));
        return boundedBody + "\n" + safeDisclosure.substring(
            0, Math.min(safeDisclosure.length(), Math.max(0, limit - boundedBody.length() - 1))
        );
    }

    private Scan scan(Path target, Charset charset, long readLimit) throws IOException {
        List<MutableSection> sections = new ArrayList<>();
        List<ContentAnchor> anchors = new ArrayList<>();
        Map<String, RepeatCounter> repeated = new LinkedHashMap<>();
        ArrayDeque<LineEntry> tail = new ArrayDeque<>(RANGE_LINES);
        long lineNumber = 0;
        long byteOffset = bomBytes(charset, target);
        try (BufferedReader reader = reader(target, charset)) {
            String line;
            while ((line = reader.readLine()) != null && byteOffset <= readLimit) {
                lineNumber++;
                long start = byteOffset;
                byteOffset += encodedLength(line, charset) + newlineBytes(charset);
                String clean = lineNumber == 1 ? stripBom(line) : line;
                collectSection(sections, clean, lineNumber);
                collectAnchors(anchors, clean, lineNumber);
                collectRepeat(repeated, clean, lineNumber);
                tail.addLast(new LineEntry(lineNumber, start, byteOffset, clean));
                if (tail.size() > RANGE_LINES) tail.removeFirst();
            }
        }
        repeated.values().stream()
            .filter(item -> item.count() >= 4)
            .sorted(Comparator.comparingInt(RepeatCounter::count).reversed())
            .limit(MAX_REPEATED)
            .forEach(item -> anchors.add(new ContentAnchor(
                "REPEATED_REGION", "重复 " + item.count() + " 次：" + item.preview(), item.firstLine()
            )));
        return new Scan(lineNumber, sections, anchors, List.copyOf(tail));
    }

    private static List<RangeRequest> representativeRanges(Scan scan, List<ContentSection> sections) {
        List<RangeRequest> requests = new ArrayList<>();
        requests.add(new RangeRequest("HEAD", 1, RANGE_LINES));
        long middle = Math.max(1, scan.lineCount() / 2);
        requests.add(new RangeRequest("MIDDLE", Math.max(1, middle - RANGE_LINES / 2), middle + RANGE_LINES / 2));
        requests.add(new RangeRequest("TAIL", Math.max(1, scan.lineCount() - RANGE_LINES + 1), scan.lineCount()));
        sections.stream().filter(section -> section.startLine() > RANGE_LINES)
            .findFirst().ifPresent(section -> requests.add(new RangeRequest(
                "HEADING", section.startLine(), Math.min(section.endLine(), section.startLine() + RANGE_LINES - 1)
            )));
        scan.anchors().stream().filter(anchor -> "SYMBOL".equals(anchor.type()))
            .findFirst().ifPresent(anchor -> requests.add(new RangeRequest(
                "SYMBOL", Math.max(1, anchor.line() - 3), anchor.line() + RANGE_LINES - 4
            )));
        scan.anchors().stream().filter(anchor -> "MARKER".equals(anchor.type()))
            .findFirst().ifPresent(anchor -> requests.add(new RangeRequest(
                "MARKER", Math.max(1, anchor.line() - 3), anchor.line() + RANGE_LINES - 4
            )));
        return requests;
    }

    private RangeSample readRangeInternal(
        Path target,
        Charset charset,
        String sourceHash,
        String kind,
        long requestedStart,
        long requestedEnd,
        int maxChars,
        long readLimit
    ) throws IOException {
        StringBuilder text = new StringBuilder();
        long lineNumber = 0;
        long byteOffset = bomBytes(charset, target);
        long actualStart = 0;
        long actualEnd = 0;
        long startByte = 0;
        long endByte = 0;
        boolean truncated = false;
        try (BufferedReader reader = reader(target, charset)) {
            String line;
            while ((line = reader.readLine()) != null && byteOffset <= readLimit) {
                lineNumber++;
                long lineStart = byteOffset;
                byteOffset += encodedLength(line, charset) + newlineBytes(charset);
                if (lineNumber < requestedStart) continue;
                if (lineNumber > requestedEnd) break;
                String clean = redactor.redact(lineNumber == 1 ? stripBom(line) : line);
                if (actualStart == 0) {
                    actualStart = lineNumber;
                    startByte = lineStart;
                }
                if (text.length() + clean.length() + 1 > maxChars) {
                    int remaining = Math.max(0, maxChars - text.length());
                    if (remaining > 0) text.append(clean, 0, Math.min(clean.length(), remaining));
                    truncated = true;
                    actualEnd = lineNumber;
                    endByte = byteOffset;
                    break;
                }
                text.append(clean).append('\n');
                actualEnd = lineNumber;
                endByte = byteOffset;
            }
        }
        if (actualEnd < requestedEnd) truncated = true;
        return new RangeSample(
            normalizeKind(kind), actualStart, actualEnd, startByte, endByte,
            sourceHash, truncated, text.toString().stripTrailing()
        );
    }

    private static List<ContentSection> closeSections(List<MutableSection> input, long lineCount) {
        List<ContentSection> result = new ArrayList<>();
        for (int index = 0; index < input.size(); index++) {
            MutableSection current = input.get(index);
            long end = index + 1 < input.size() ? input.get(index + 1).startLine() - 1 : lineCount;
            result.add(new ContentSection(current.type(), current.title(), current.startLine(), Math.max(current.startLine(), end)));
        }
        return List.copyOf(result);
    }

    private static void collectSection(List<MutableSection> sections, String line, long lineNumber) {
        if (sections.size() >= MAX_SECTIONS) return;
        Matcher heading = MARKDOWN_HEADING.matcher(line);
        if (heading.matches()) {
            sections.add(new MutableSection("HEADING_" + heading.group(1).length(), bounded(heading.group(2), 180), lineNumber));
            return;
        }
        String stripped = line.strip();
        if (stripped.matches("(?i)^(chapter|section|part)\\s+[\\w.-]+.*")
            || stripped.matches("^第[一二三四五六七八九十百0-9]+[章节部分].*")) {
            sections.add(new MutableSection("TEXT_SECTION", bounded(stripped, 180), lineNumber));
        }
    }

    private static void collectAnchors(List<ContentAnchor> anchors, String line, long lineNumber) {
        if (anchors.size() >= MAX_ANCHORS) return;
        Matcher symbol = SYMBOL.matcher(line);
        if (symbol.find()) {
            anchors.add(new ContentAnchor("SYMBOL", bounded(symbol.group(1), 140), lineNumber));
        }
        Matcher marker = MARKER.matcher(line);
        if (marker.find() && anchors.size() < MAX_ANCHORS) {
            anchors.add(new ContentAnchor("MARKER", bounded(line.strip(), 180), lineNumber));
        }
    }

    private static void collectRepeat(Map<String, RepeatCounter> repeated, String line, long lineNumber) {
        String normalized = line.strip().replaceAll("\\s+", " ");
        if (normalized.length() < 24 || normalized.length() > 240) return;
        String key = Integer.toHexString(normalized.hashCode());
        RepeatCounter current = repeated.get(key);
        repeated.put(key, current == null
            ? new RepeatCounter(1, lineNumber, bounded(normalized, 120))
            : new RepeatCounter(current.count() + 1, current.firstLine(), current.preview()));
    }

    private static List<String> unreadRanges(
        long lineCount,
        List<RangeSample> samples,
        boolean partial,
        long byteSize,
        long readLimit
    ) {
        List<long[]> ranges = samples.stream()
            .filter(sample -> sample.startLine() > 0 && sample.endLine() >= sample.startLine())
            .map(sample -> new long[] {sample.startLine(), sample.endLine()})
            .sorted(Comparator.comparingLong(value -> value[0]))
            .toList();
        List<String> unread = new ArrayList<>();
        long cursor = 1;
        for (long[] range : ranges) {
            if (range[0] > cursor) unread.add("lines " + cursor + "-" + (range[0] - 1));
            cursor = Math.max(cursor, range[1] + 1);
            if (unread.size() >= 12) break;
        }
        if (cursor <= lineCount && unread.size() < 12) unread.add("lines " + cursor + "-" + lineCount);
        if (partial) unread.add("bytes " + readLimit + "-" + byteSize);
        return List.copyOf(unread);
    }

    private static byte[] readProbe(Path target) throws IOException {
        try (var input = Files.newInputStream(target)) {
            return input.readNBytes(PROBE_BYTES);
        }
    }

    private static Encoding detectEncoding(byte[] probe) {
        if (probe.length >= 2 && probe[0] == (byte) 0xFF && probe[1] == (byte) 0xFE) {
            return new Encoding("UTF-16LE", StandardCharsets.UTF_16LE, false);
        }
        if (probe.length >= 2 && probe[0] == (byte) 0xFE && probe[1] == (byte) 0xFF) {
            return new Encoding("UTF-16BE", StandardCharsets.UTF_16BE, false);
        }
        int nul = 0;
        int evenNul = 0;
        int oddNul = 0;
        for (int index = 0; index < probe.length; index++) {
            if (probe[index] == 0) {
                nul++;
                if ((index & 1) == 0) evenNul++; else oddNul++;
            }
        }
        if (nul > 0) {
            if (oddNul > probe.length / 8) return new Encoding("UTF-16LE", StandardCharsets.UTF_16LE, false);
            if (evenNul > probe.length / 8) return new Encoding("UTF-16BE", StandardCharsets.UTF_16BE, false);
            return new Encoding("BINARY", StandardCharsets.UTF_8, true);
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(probe));
            return new Encoding("UTF-8", StandardCharsets.UTF_8, false);
        } catch (CharacterCodingException exception) {
            return new Encoding("ISO-8859-1", StandardCharsets.ISO_8859_1, false);
        }
    }

    private static String hash(Path target, long readLimit, boolean partial) throws IOException {
        MessageDigest digest = sha256();
        if (!partial) {
            try (DigestInputStream input = new DigestInputStream(
                new BufferedInputStream(Files.newInputStream(target)), digest
            )) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        long byteSize = Files.size(target);
        digest.update(Long.toString(byteSize).getBytes(StandardCharsets.UTF_8));
        long headBudget = Math.max(1, readLimit / 2);
        long tailBudget = Math.max(1, readLimit - headBudget);
        try (var channel = Files.newByteChannel(target, StandardOpenOption.READ)) {
            updateDigest(channel, digest, 0, headBudget);
            updateDigest(channel, digest, Math.max(headBudget, byteSize - tailBudget), tailBudget);
        }
        return "partial:" + HexFormat.of().formatHex(digest.digest());
    }

    private static void updateDigest(
        java.nio.channels.SeekableByteChannel channel,
        MessageDigest digest,
        long start,
        long limit
    ) throws IOException {
        channel.position(start);
        ByteBuffer buffer = ByteBuffer.allocate(16_384);
        long remaining = limit;
        while (remaining > 0) {
            buffer.clear();
            buffer.limit((int) Math.min(buffer.capacity(), remaining));
            int read = channel.read(buffer);
            if (read < 0) break;
            digest.update(buffer.array(), 0, read);
            remaining -= read;
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    private static BufferedReader reader(Path target, Charset charset) throws IOException {
        return new BufferedReader(new InputStreamReader(Files.newInputStream(target), charset), 64 * 1024);
    }

    private static long bomBytes(Charset charset, Path target) {
        String name = charset.name();
        if (name.startsWith("UTF-16")) return 2;
        return 0;
    }

    private static int encodedLength(String value, Charset charset) {
        return value.getBytes(charset).length;
    }

    private static int newlineBytes(Charset charset) {
        return charset.name().startsWith("UTF-16") ? 2 : 1;
    }

    private static String stripBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        String safe = value.strip();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private static String normalizeKind(String value) {
        if (value == null || value.isBlank()) return "TARGETED";
        return value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
    }

    private static ContentMap unavailable(String limitation) {
        return new ContentMap(
            "UNKNOWN", false, 0, 0, "", true,
            List.of(), List.of(), List.of(), List.of(), List.of(limitation)
        );
    }

    public record ContentMap(
        String encoding,
        boolean binary,
        long byteSize,
        long lineCount,
        String sourceHash,
        boolean partial,
        List<ContentSection> sections,
        List<ContentAnchor> anchors,
        List<RangeSample> samples,
        List<String> unreadRanges,
        List<String> limitations
    ) {
    }

    public record ContentSection(String type, String title, long startLine, long endLine) {
    }

    public record ContentAnchor(String type, String text, long line) {
    }

    public record RangeSample(
        String kind,
        long startLine,
        long endLine,
        long startByte,
        long endByte,
        String sourceHash,
        boolean truncated,
        String text
    ) {
    }

    private record Encoding(String name, Charset charset, boolean binary) {
    }

    private record MutableSection(String type, String title, long startLine) {
    }

    private record RangeRequest(String kind, long startLine, long endLine) {
    }

    private record LineEntry(long line, long startByte, long endByte, String text) {
    }

    private record RepeatCounter(int count, long firstLine, String preview) {
    }

    private record Scan(
        long lineCount,
        List<MutableSection> sections,
        List<ContentAnchor> anchors,
        List<LineEntry> tail
    ) {
    }
}
