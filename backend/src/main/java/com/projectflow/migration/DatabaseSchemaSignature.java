package com.projectflow.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Computes the bounded, dialect-neutral identity of the V3.9 schema.
 *
 * <p>The V1 SQL is generated from the V3.9 entity model and is also the
 * frozen representative fixture.  JDBC type names are reduced to families so
 * H2 ENUM/VARCHAR and PostgreSQL VARCHAR remain comparable, while table and
 * column membership plus nullability remain exact.</p>
 */
@Component
public final class DatabaseSchemaSignature {
    private static final String PUBLIC_SCHEMA = "public";
    private static final String FLYWAY_HISTORY = "flyway_schema_history";
    private static final String BASELINE_RESOURCE = "db/migration/V1__v39_schema_baseline.sql";
    private static final Pattern CREATE_TABLE = Pattern.compile(
        "(?is)CREATE\\s+TABLE\\s+\\\"public\\\"\\.\\\"([^\\\"]+)\\\"\\s*\\((.*?)\\);"
    );
    private static final Pattern PRIMARY_KEY = Pattern.compile(
        "(?is)ALTER\\s+TABLE\\s+\\\"public\\\"\\.\\\"([^\\\"]+)\\\"[^;]*?PRIMARY\\s+KEY\\s*\\(([^)]*)\\)"
    );
    private static final Pattern UNIQUE_CONSTRAINT = Pattern.compile(
        "(?is)ALTER\\s+TABLE\\s+\\\"public\\\"\\.\\\"([^\\\"]+)\\\"\\s+ADD\\s+CONSTRAINT\\s+\\\"([^\\\"]+)\\\"[^;]*?UNIQUE\\s*\\(([^)]*)\\)"
    );
    private static final Pattern CREATE_INDEX = Pattern.compile(
        "(?is)CREATE\\s+(UNIQUE\\s+)?INDEX\\s+(?:\\\"public\\\"\\.)?\\\"([^\\\"]+)\\\"\\s+ON\\s+\\\"public\\\"\\.\\\"([^\\\"]+)\\\"\\s*\\(([^)]*)\\)"
    );
    private static final Pattern FOREIGN_KEY = Pattern.compile(
        "(?is)ALTER\\s+TABLE\\s+\\\"public\\\"\\.\\\"([^\\\"]+)\\\"[^;]*?FOREIGN\\s+KEY\\s*\\(([^)]*)\\)\\s+REFERENCES\\s+\\\"public\\\"\\.\\\"([^\\\"]+)\\\"\\s*\\(([^)]*)\\)"
    );
    private static final Pattern QUOTED_IDENTIFIER = Pattern.compile("\\\"([^\\\"]+)\\\"");

    private final ExpectedSchemas expected;

    public DatabaseSchemaSignature() {
        this.expected = parseExpectedBaseline();
    }

    public Inspection inspect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return inspect(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("SCHEMA_INSPECTION_FAILED", exception);
        }
    }

    public Inspection inspect(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String dialect = dialect(metadata.getDatabaseProductName());
        Set<String> tables = userTables(metadata);
        boolean hasFlywayHistory = hasTable(metadata, FLYWAY_HISTORY);
        Set<String> actualEntries = schemaEntries(metadata, tables);
        actualEntries.addAll(publicObjectEntries(connection, metadata, tables, dialect));
        if (tables.isEmpty() && actualEntries.isEmpty()) {
            return new Inspection(
                SchemaClassification.EMPTY,
                dialect,
                hasFlywayHistory,
                fingerprint(List.of()),
                expected.v39.fingerprint,
                Set.of(),
                Set.of()
            );
        }

        String actualFingerprint = fingerprint(actualEntries);
        SchemaClassification classification;
        Signature comparisonTarget;
        if (actualFingerprint.equals(expected.v39.fingerprint)) {
            classification = SchemaClassification.KNOWN_V39;
            comparisonTarget = expected.v39;
        } else if (actualFingerprint.equals(expected.current.fingerprint)) {
            classification = SchemaClassification.KNOWN_CURRENT;
            comparisonTarget = expected.current;
        } else {
            classification = SchemaClassification.UNKNOWN;
            comparisonTarget = expected.v39;
        }
        Set<String> missing = new TreeSet<>(comparisonTarget.entries);
        missing.removeAll(actualEntries);
        Set<String> extra = new TreeSet<>(actualEntries);
        extra.removeAll(comparisonTarget.entries);
        return new Inspection(
            classification,
            dialect,
            hasFlywayHistory,
            actualFingerprint,
            comparisonTarget.fingerprint,
            missing,
            extra
        );
    }

    private Set<String> userTables(DatabaseMetaData metadata) throws SQLException {
        Set<String> tables = new TreeSet<>();
        try (ResultSet result = metadata.getTables(null, null, "%", new String[] {"TABLE"})) {
            while (result.next()) {
                String schema = lower(result.getString("TABLE_SCHEM"));
                String table = lower(result.getString("TABLE_NAME"));
                if (PUBLIC_SCHEMA.equals(schema) && !FLYWAY_HISTORY.equals(table)) tables.add(table);
            }
        }
        return tables;
    }

    private boolean hasTable(DatabaseMetaData metadata, String expectedName) throws SQLException {
        try (ResultSet result = metadata.getTables(null, null, "%", new String[] {"TABLE"})) {
            while (result.next()) {
                if (PUBLIC_SCHEMA.equals(lower(result.getString("TABLE_SCHEM")))
                    && expectedName.equals(lower(result.getString("TABLE_NAME")))) return true;
            }
        }
        return false;
    }

    private Set<String> publicObjectEntries(
        Connection connection,
        DatabaseMetaData metadata,
        Set<String> tables,
        String dialect
    ) throws SQLException {
        Set<String> objects = new TreeSet<>();
        for (String requestedType : List.of("VIEW", "MATERIALIZED VIEW")) {
            try (ResultSet result = metadata.getTables(null, PUBLIC_SCHEMA, "%", new String[] {requestedType})) {
                while (result.next()) {
                    String table = lower(result.getString("TABLE_NAME"));
                    if (table.isBlank() || FLYWAY_HISTORY.equals(table) || tables.contains(table)) continue;
                    String type = lower(result.getString("TABLE_TYPE"));
                    objects.add("object." + (type.isBlank() ? requestedType.toLowerCase(Locale.ROOT) : type)
                        + "." + table);
                }
            }
        }
        if ("h2".equalsIgnoreCase(dialect) || "postgresql".equalsIgnoreCase(dialect)) {
            try (var statement = connection.prepareStatement(
                "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ?"
            )) {
                statement.setString(1, PUBLIC_SCHEMA);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String sequence = lower(result.getString("sequence_name"));
                        if (!sequence.isBlank()) objects.add("object.sequence." + sequence);
                    }
                }
            }
        }
        return objects;
    }

    private Set<String> schemaEntries(DatabaseMetaData metadata, Set<String> tables) throws SQLException {
        Set<String> entries = userColumns(metadata, tables);
        Map<String, Set<String>> primaryColumns = primaryKeys(metadata, tables, entries);
        entries.addAll(indexes(metadata, tables, primaryColumns));
        entries.addAll(foreignKeys(metadata, tables));
        return entries;
    }

    private Set<String> userColumns(DatabaseMetaData metadata, Set<String> tables) throws SQLException {
        Set<String> columns = new TreeSet<>();
        for (String table : tables) {
            try (ResultSet result = metadata.getColumns(null, null, table, "%")) {
                while (result.next()) {
                    if (!PUBLIC_SCHEMA.equals(lower(result.getString("TABLE_SCHEM")))) continue;
                    String name = lower(result.getString("COLUMN_NAME"));
                    String type = typeFamily(result.getString("TYPE_NAME"));
                    int nullable = result.getInt("NULLABLE");
                    String nullability = nullable == DatabaseMetaData.columnNoNulls
                        ? "required"
                        : nullable == DatabaseMetaData.columnNullable
                            ? "nullable"
                            : "unknown";
                    columns.add(table + "." + name + "." + type + "." + nullability);
                }
            }
        }
        return columns;
    }

    private Map<String, Set<String>> primaryKeys(
        DatabaseMetaData metadata,
        Set<String> tables,
        Set<String> entries
    ) throws SQLException {
        Map<String, List<IndexedColumn>> grouped = new HashMap<>();
        for (String table : tables) {
            try (ResultSet result = metadata.getPrimaryKeys(null, PUBLIC_SCHEMA, table)) {
                while (result.next()) {
                    String column = lower(result.getString("COLUMN_NAME"));
                    if (column.isBlank()) continue;
                    String keyName = lower(result.getString("PK_NAME"));
                    String key = table + "|" + keyName;
                    grouped.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new IndexedColumn(column, result.getShort("KEY_SEQ")));
                }
            }
        }
        Map<String, Set<String>> primaryColumns = new HashMap<>();
        for (Map.Entry<String, List<IndexedColumn>> entry : grouped.entrySet()) {
            String table = entry.getKey().substring(0, entry.getKey().indexOf('|'));
            List<String> columns = orderedColumns(entry.getValue());
            if (!columns.isEmpty()) {
                String identity = String.join(",", columns);
                entries.add("primary." + table + "." + identity);
                primaryColumns.put(table, Set.of(identity));
            }
        }
        return primaryColumns;
    }

    private Set<String> indexes(
        DatabaseMetaData metadata,
        Set<String> tables,
        Map<String, Set<String>> primaryColumns
    ) throws SQLException {
        Set<String> indexes = new TreeSet<>();
        for (String table : tables) {
            Map<String, IndexData> grouped = new HashMap<>();
            try (ResultSet result = metadata.getIndexInfo(null, PUBLIC_SCHEMA, table, false, false)) {
                while (result.next()) {
                    short type = result.getShort("TYPE");
                    String column = lower(result.getString("COLUMN_NAME"));
                    String name = lower(result.getString("INDEX_NAME"));
                    if (type == DatabaseMetaData.tableIndexStatistic || column.isBlank() || name.isBlank()) continue;
                    boolean unique = !result.getBoolean("NON_UNIQUE");
                    grouped.computeIfAbsent(name, ignored -> new IndexData(name, unique, new ArrayList<>()))
                        .columns().add(new IndexedColumn(column, result.getShort("ORDINAL_POSITION")));
                }
            }
            for (IndexData index : grouped.values()) {
                String identity = String.join(",", orderedColumns(index.columns()));
                if (identity.isBlank()) continue;
                if (index.unique() && primaryColumns.getOrDefault(table, Set.of()).contains(identity)) continue;
                indexes.add(index.unique()
                    ? "unique." + table + "." + identity
                    : "index." + table + "." + index.name() + "." + identity);
            }
        }
        return indexes;
    }

    private Set<String> foreignKeys(DatabaseMetaData metadata, Set<String> tables) throws SQLException {
        Map<String, ForeignKeyData> grouped = new HashMap<>();
        for (String table : tables) {
            try (ResultSet result = metadata.getImportedKeys(null, PUBLIC_SCHEMA, table)) {
                while (result.next()) {
                    String fkName = lower(result.getString("FK_NAME"));
                    String foreignTable = lower(result.getString("PKTABLE_NAME"));
                    String localColumn = lower(result.getString("FKCOLUMN_NAME"));
                    String foreignColumn = lower(result.getString("PKCOLUMN_NAME"));
                    if (localColumn.isBlank() || foreignTable.isBlank() || foreignColumn.isBlank()) continue;
                    String key = table + "|" + fkName + "|" + foreignTable;
                    grouped.computeIfAbsent(key, ignored -> new ForeignKeyData(new ArrayList<>()))
                        .columns().add(new ForeignColumn(localColumn, foreignColumn, result.getShort("KEY_SEQ")));
                }
            }
        }
        Set<String> keys = new TreeSet<>();
        for (Map.Entry<String, ForeignKeyData> entry : grouped.entrySet()) {
            String[] keyParts = entry.getKey().split("\\|", 3);
            List<ForeignColumn> columns = new ArrayList<>(entry.getValue().columns());
            columns.sort(java.util.Comparator.comparingInt(ForeignColumn::sequence));
            String local = columns.stream().map(ForeignColumn::local).reduce((a, b) -> a + "," + b).orElse("");
            String foreign = columns.stream().map(ForeignColumn::foreignColumn).reduce((a, b) -> a + "," + b).orElse("");
            if (!local.isBlank() && !foreign.isBlank()) {
                keys.add("foreign." + keyParts[0] + "." + local + "->" + keyParts[2] + "." + foreign);
            }
        }
        return keys;
    }

    private List<String> orderedColumns(List<IndexedColumn> columns) {
        List<IndexedColumn> sorted = new ArrayList<>(columns);
        sorted.sort(java.util.Comparator.comparingInt(IndexedColumn::sequence));
        return sorted.stream().map(IndexedColumn::name).toList();
    }

    private ExpectedSchemas parseExpectedBaseline() {
        try (InputStream input = new ClassPathResource(BASELINE_RESOURCE).getInputStream()) {
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Set<String> entries = new TreeSet<>();
            Matcher tableMatcher = CREATE_TABLE.matcher(sql);
            while (tableMatcher.find()) {
                String table = lower(tableMatcher.group(1));
                String[] lines = tableMatcher.group(2).split("\\R");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("\"")) continue;
                    int end = trimmed.indexOf('"', 1);
                    if (end < 0) continue;
                    String column = lower(trimmed.substring(1, end));
                    String definition = trimmed.substring(end + 1).trim();
                    if (definition.endsWith(",")) definition = definition.substring(0, definition.length() - 1).trim();
                    boolean nullable = !definition.toUpperCase(Locale.ROOT).contains(" NOT NULL");
                    entries.add(table + "." + column + "." + typeFamily(definition) + "."
                        + (nullable ? "nullable" : "required"));
                }
            }
            Matcher primaryMatcher = PRIMARY_KEY.matcher(sql);
            while (primaryMatcher.find()) {
                List<String> columns = parseColumnList(primaryMatcher.group(2));
                if (!columns.isEmpty()) entries.add("primary." + lower(primaryMatcher.group(1)) + "." + String.join(",", columns));
            }
            Matcher uniqueMatcher = UNIQUE_CONSTRAINT.matcher(sql);
            while (uniqueMatcher.find()) {
                List<String> columns = parseColumnList(uniqueMatcher.group(3));
                if (!columns.isEmpty()) entries.add("unique." + lower(uniqueMatcher.group(1)) + "."
                    + String.join(",", columns));
            }
            Matcher indexMatcher = CREATE_INDEX.matcher(sql);
            while (indexMatcher.find()) {
                List<String> columns = parseColumnList(indexMatcher.group(4));
                if (!columns.isEmpty()) {
                    entries.add(indexMatcher.group(1) == null
                        ? "index." + lower(indexMatcher.group(3)) + "." + lower(indexMatcher.group(2)) + "."
                            + String.join(",", columns)
                        : "unique." + lower(indexMatcher.group(3)) + "." + String.join(",", columns));
                }
            }
            Matcher foreignMatcher = FOREIGN_KEY.matcher(sql);
            while (foreignMatcher.find()) {
                List<String> local = parseColumnList(foreignMatcher.group(2));
                List<String> foreign = parseColumnList(foreignMatcher.group(4));
                if (!local.isEmpty() && !foreign.isEmpty()) {
                    entries.add("foreign." + lower(foreignMatcher.group(1)) + "." + String.join(",", local)
                        + "->" + lower(foreignMatcher.group(3)) + "." + String.join(",", foreign));
                }
            }
            if (entries.isEmpty()) throw new IllegalStateException("V39_SCHEMA_SIGNATURE_EMPTY");
            Set<String> currentEntries = new TreeSet<>(entries);
            currentEntries.add("ai_providers.secret_ref.string.nullable");
            return new ExpectedSchemas(
                new Signature(entries, fingerprint(entries)),
                new Signature(currentEntries, fingerprint(currentEntries))
            );
        } catch (IOException exception) {
            throw new IllegalStateException("V39_SCHEMA_SIGNATURE_UNAVAILABLE", exception);
        }
    }

    private List<String> parseColumnList(String raw) {
        List<String> columns = new ArrayList<>();
        Matcher matcher = QUOTED_IDENTIFIER.matcher(raw == null ? "" : raw);
        while (matcher.find()) columns.add(lower(matcher.group(1)));
        if (!columns.isEmpty()) return columns;
        if (raw == null || raw.isBlank()) return List.of();
        for (String value : raw.split(",")) {
            String normalized = value.trim().replaceAll("\\s+.*$", "");
            if (!normalized.isBlank()) columns.add(lower(normalized));
        }
        return columns;
    }

    private String fingerprint(Set<String> entries) {
        return fingerprint(new ArrayList<>(entries));
    }

    private String fingerprint(List<String> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            entries.stream().sorted().forEach(entry -> {
                digest.update(entry.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            });
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String typeFamily(String rawType) {
        String type = rawType == null ? "" : rawType.toUpperCase(Locale.ROOT);
        if (type.contains("CHAR") || type.contains("TEXT") || type.contains("CLOB") || type.contains("ENUM")) return "string";
        if (type.contains("UUID")) return "uuid";
        if (type.contains("TIMESTAMP")) return "timestamp";
        if (type.contains("DATE")) return "date";
        if (type.contains("TIME")) return "time";
        if (type.contains("BOOL")) return "boolean";
        if (type.contains("INT") || type.contains("SERIAL")) return "integer";
        if (type.contains("DECIMAL") || type.contains("NUMERIC") || type.contains("FLOAT")
            || type.contains("DOUBLE") || type.contains("REAL")) return "decimal";
        if (type.contains("BINARY") || type.contains("BYTEA")) return "binary";
        return type.replaceAll("\\s+", " ").trim();
    }

    private String dialect(String productName) {
        String product = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
        if (product.contains("h2")) return "h2";
        if (product.contains("postgres")) return "postgresql";
        return product.isBlank() ? "unknown" : product;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public enum SchemaClassification {
        EMPTY,
        KNOWN_V39,
        KNOWN_CURRENT,
        UNKNOWN
    }

    public record Inspection(
        SchemaClassification classification,
        String dialect,
        boolean hasFlywayHistory,
        String actualFingerprint,
        String expectedFingerprint,
        Set<String> missingEntries,
        Set<String> extraEntries
    ) {
        public boolean matchesKnownV39() {
            return classification == SchemaClassification.KNOWN_V39;
        }

        public boolean matchesRestorableSchema() {
            return classification == SchemaClassification.KNOWN_V39
                || classification == SchemaClassification.KNOWN_CURRENT;
        }
    }

    private record Signature(Set<String> entries, String fingerprint) {}

    private record ExpectedSchemas(Signature v39, Signature current) {}

    private record IndexedColumn(String name, int sequence) {}

    private record IndexData(String name, boolean unique, List<IndexedColumn> columns) {}

    private record ForeignColumn(String local, String foreignColumn, int sequence) {}

    private record ForeignKeyData(List<ForeignColumn> columns) {}
}
