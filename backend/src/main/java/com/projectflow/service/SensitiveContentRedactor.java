package com.projectflow.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Small outbound trust-boundary filter. It borrows the mature
 * keyword + format + entropy layering used by secret scanners without
 * embedding a repository-wide scanner or provider verification runtime.
 */
@Component
public class SensitiveContentRedactor {
    public static final String REDACTED = "[REDACTED_SECRET]";

    private static final List<Pattern> SECRET_PATTERNS = List.of(
        Pattern.compile("(?s)-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----.*?-----END (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
        Pattern.compile("\\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\\b"),
        Pattern.compile("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b"),
        Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"),
        Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{16,}"),
        Pattern.compile("(?i)\\b(?:postgres(?:ql)?|mysql|mongodb(?:\\+srv)?|redis)://[^\\s/:@]+:[^\\s@]+@[^\\s]+"),
        Pattern.compile(
            "(?i)(\\b(?:api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|passwd|authorization|credential)\\b\\s*[:=]\\s*)"
                + "[\"']?[^\\s,\"';}]{8,}[\"']?"
        )
    );
    private static final Pattern ENTROPY_CANDIDATE = Pattern.compile(
        "(?<![A-Za-z0-9])[A-Za-z0-9_+/=-]{40,160}(?![A-Za-z0-9])"
    );
    private static final Pattern HEX_DIGEST = Pattern.compile("(?i)[a-f0-9]{40}|[a-f0-9]{64}");

    public String redact(String value) {
        if (value == null || value.isEmpty()) return "";
        String result = value;
        for (Pattern pattern : SECRET_PATTERNS) {
            result = pattern.matcher(result).replaceAll(
                match -> Matcher.quoteReplacement(preserveLabel(match))
            );
        }
        return redactHighEntropy(result);
    }

    public boolean containsSensitive(String value) {
        return !redact(value).equals(value == null ? "" : value);
    }

    public boolean isSensitivePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return false;
        String lower = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        return (fileName.startsWith(".env") && !fileName.equals(".env.example"))
            || fileName.endsWith(".pem")
            || fileName.endsWith(".key")
            || fileName.endsWith(".p12")
            || fileName.endsWith(".pfx")
            || lower.contains("/credentials/")
            || lower.contains("credentials.")
            || lower.contains("/secrets/")
            || lower.contains("secret.");
    }

    private static String preserveLabel(MatchResult match) {
        String value = match.group();
        int separator = Math.max(value.indexOf('='), value.indexOf(':'));
        if (separator >= 0 && separator < 80) {
            return value.substring(0, separator + 1) + REDACTED;
        }
        return REDACTED;
    }

    private static String redactHighEntropy(String value) {
        Matcher matcher = ENTROPY_CANDIDATE.matcher(value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String candidate = matcher.group();
            boolean redact = !HEX_DIGEST.matcher(candidate).matches()
                && hasMixedClasses(candidate)
                && shannonEntropy(candidate) >= 4.5
                && !looksLikeExample(candidate);
            matcher.appendReplacement(output, Matcher.quoteReplacement(redact ? REDACTED : candidate));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static boolean hasMixedClasses(String value) {
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            lower |= Character.isLowerCase(current);
            upper |= Character.isUpperCase(current);
            digit |= Character.isDigit(current);
        }
        return lower && upper && digit;
    }

    private static double shannonEntropy(String value) {
        int[] counts = new int[128];
        int counted = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current < counts.length) {
                counts[current]++;
                counted++;
            }
        }
        if (counted == 0) return 0;
        double entropy = 0;
        for (int count : counts) {
            if (count == 0) continue;
            double probability = (double) count / counted;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        return entropy;
    }

    private static boolean looksLikeExample(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("example")
            || lower.contains("placeholder")
            || lower.contains("replace_me")
            || lower.contains("your_token")
            || lower.chars().distinct().count() < 12;
    }

}
