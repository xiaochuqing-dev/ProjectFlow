package com.projectflow.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.projectflow.entity.ProjectChangeSourceType;

import org.springframework.boot.context.event.ApplicationReadyEvent;

@Service
public class ProjectChangeSchemaRepairService {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public ProjectChangeSchemaRepairService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void repairOnStartup() {
        ensureEvidenceBundleSourceTypeAllowed();
    }

    public void ensureEvidenceBundleSourceTypeAllowed() {
        if (!isH2Database()) {
            return;
        }

        String sourceType = jdbcTemplate.query(
            """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_name = 'project_changes'
                  AND column_name = 'source_type'
                """,
            resultSet -> resultSet.next() ? resultSet.getString("data_type") : ""
        );
        if (!"enum".equalsIgnoreCase(sourceType)) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE project_changes ALTER COLUMN source_type " + h2EnumDefinition());
    }

    private boolean isH2Database() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
        } catch (SQLException exception) {
            return false;
        }
    }

    private String h2EnumDefinition() {
        return Arrays.stream(ProjectChangeSourceType.values())
            .map(Enum::name)
            .map(value -> "'" + value + "'")
            .collect(Collectors.joining(",", "ENUM(", ")"));
    }
}
