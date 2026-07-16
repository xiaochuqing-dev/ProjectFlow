package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.projectflow.entity.EvidenceConfidence;
import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.ProjectFactOrigin;
import com.projectflow.entity.ProjectFactRecordStatus;
import com.projectflow.entity.TimelineGranularity;

class TimelinePeriodResolverTest {
    private final TimelinePeriodResolver utc = TimelinePeriodResolver.fixed(ZoneId.of("UTC"));

    @ParameterizedTest
    @CsvSource({
        "2026-01-01T00:00:00Z,2026-01-01,2026-W01,2026-01",
        "2026-01-04T23:59:59Z,2026-01-04,2026-W01,2026-01",
        "2026-01-05T00:00:00Z,2026-01-05,2026-W02,2026-01",
        "2026-06-29T10:00:00Z,2026-06-29,2026-W27,2026-06",
        "2026-07-01T10:00:00Z,2026-07-01,2026-W27,2026-07",
        "2026-07-15T10:00:00Z,2026-07-15,2026-W29,2026-07",
        "2026-12-31T23:59:59Z,2026-12-31,2026-W53,2026-12",
        "2027-01-01T00:00:00Z,2027-01-01,2026-W53,2027-01",
        "2027-01-04T00:00:00Z,2027-01-04,2027-W01,2027-01",
        "2024-02-29T12:00:00Z,2024-02-29,2024-W09,2024-02"
    })
    void resolvesDayIsoWeekAndMonth(String instant, String day, String week, String month) {
        var assignment = utc.assign(fact(Instant.parse(instant), Instant.parse(instant)));
        assertThat(assignment.dayKey()).isEqualTo(day);
        assertThat(assignment.weekKey()).isEqualTo(week);
        assertThat(assignment.monthKey()).isEqualTo(month);
    }

    @ParameterizedTest
    @CsvSource({
        "DAY,2026-07-15,2026-07-15T00:00:00Z,2026-07-16T00:00:00Z",
        "WEEK,2026-W29,2026-07-13T00:00:00Z,2026-07-20T00:00:00Z",
        "WEEK,2026-W01,2025-12-29T00:00:00Z,2026-01-05T00:00:00Z",
        "MONTH,2026-07,2026-07-01T00:00:00Z,2026-08-01T00:00:00Z",
        "MONTH,2024-02,2024-02-01T00:00:00Z,2024-03-01T00:00:00Z"
    })
    void resolvesCanonicalPeriodRanges(String granularity, String key, String start, String end) {
        var range = utc.resolve(TimelineGranularity.valueOf(granularity), key);
        assertThat(range.startInclusive()).isEqualTo(Instant.parse(start));
        assertThat(range.endExclusive()).isEqualTo(Instant.parse(end));
    }

    @Test
    void lifecycleUsesAllKey() {
        var range = utc.resolve(TimelineGranularity.LIFECYCLE, "ALL");
        assertThat(range.periodKey()).isEqualTo("ALL");
        assertThat(range.startInclusive()).isNull();
        assertThat(range.endExclusive()).isNull();
    }

    @Test
    void configuredZoneCanCrossUtcDate() {
        var shanghai = TimelinePeriodResolver.fixed(ZoneId.of("Asia/Shanghai"));
        var assignment = shanghai.assign(fact(
            Instant.parse("2026-07-15T16:30:00Z"), Instant.parse("2026-07-15T16:30:00Z")
        ));
        assertThat(assignment.dayKey()).isEqualTo("2026-07-16");
        assertThat(shanghai.zoneId()).isEqualTo("Asia/Shanghai");
    }

    @Test
    void occurredToIsPrimaryAssignmentTime() {
        var assignment = utc.assign(fact(
            Instant.parse("2026-06-30T23:00:00Z"), Instant.parse("2026-07-01T01:00:00Z")
        ));
        assertThat(assignment.monthKey()).isEqualTo("2026-07");
        assertThat(assignment.eventAt()).isEqualTo(Instant.parse("2026-07-01T01:00:00Z"));
    }

    @ParameterizedTest
    @CsvSource({
        "DAY,2026-02-30",
        "DAY,2026/07/15",
        "WEEK,2026-W00",
        "WEEK,2026-W54",
        "WEEK,2026-W7",
        "MONTH,2026-13",
        "MONTH,2026/07",
        "LIFECYCLE,CURRENT"
    })
    void rejectsInvalidPeriodKeys(String granularity, String key) {
        assertThatThrownBy(() -> utc.resolve(TimelineGranularity.valueOf(granularity), key))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private ProjectFact fact(Instant from, Instant to) {
        ProjectFact fact = new ProjectFact(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            ProjectFactOrigin.INCREMENTAL_SCAN, UUID.randomUUID().toString().replace("-", "") + "00000000000000000000000000000000");
        fact.updateContent(
            "事实", "摘要", List.of("变化"), "价值", from, to, List.of(), List.of(), List.of(), List.of(), List.of(),
            "MODEL", "PASS", EvidenceConfidence.HIGH, ProjectFactRecordStatus.RECORDED, ""
        );
        return fact;
    }
}
