package com.projectflow.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.projectflow.entity.ProjectAnalysisJobType;
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
        ensureAnalysisJobTypeAllowed();
        // ChangeBatch 的 4 个耗时字段在 v3.3.2 才加入，ddl-auto:update 只加列不补 NOT NULL，
        // 老行这 4 列为 NULL。实体已改成 Long 可容忍，这里再把存量 NULL 补 0，让数据恢复正常。
        backfillChangeBatchTimingNulls();
    }

    public void backfillChangeBatchTimingNulls() {
        jdbcTemplate.update(
            """
                UPDATE change_batches
                SET git_scan_ms = 0,
                    model_segment_ms = 0,
                    github_inspect_ms = 0,
                    total_scan_ms = 0
                WHERE git_scan_ms IS NULL
                   OR model_segment_ms IS NULL
                   OR github_inspect_ms IS NULL
                   OR total_scan_ms IS NULL
                """
        );
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

    public void ensureAnalysisJobTypeAllowed() {
        if (!isH2Database()) {
            return;
        }

        String jobType = jdbcTemplate.query(
            """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_name = 'project_analysis_jobs'
                  AND column_name = 'job_type'
                """,
            resultSet -> resultSet.next() ? resultSet.getString("data_type") : ""
        );
        if (!"enum".equalsIgnoreCase(jobType)) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE project_analysis_jobs ALTER COLUMN job_type " + h2JobTypeEnumDefinition());
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

    private String h2JobTypeEnumDefinition() {
        return Arrays.stream(ProjectAnalysisJobType.values())
            .map(Enum::name)
            .map(value -> "'" + value + "'")
            .collect(Collectors.joining(",", "ENUM(", ")"));
    }
}
