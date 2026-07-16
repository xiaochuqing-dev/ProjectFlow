package com.projectflow.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.projectflow.entity.ProjectFact;
import com.projectflow.entity.TimelineGranularity;

@Component
public class TimelinePeriodResolver {
    private static final Pattern WEEK_KEY = Pattern.compile("^(\\d{4})-W(\\d{2})$");
    private static final Pattern DAY_KEY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern MONTH_KEY = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final WeekFields ISO = WeekFields.ISO;

    private final ZoneId zone;

    @Autowired
    public TimelinePeriodResolver(@Value("${projectflow.timeline.zone:}") String configuredZone) {
        this(resolveZone(configuredZone));
    }

    TimelinePeriodResolver(ZoneId zone) {
        this.zone = zone;
    }

    public static TimelinePeriodResolver fixed(ZoneId zone) {
        return new TimelinePeriodResolver(zone);
    }

    public Assignment assign(ProjectFact fact) {
        Instant eventAt = fact.getOccurredTo() != null ? fact.getOccurredTo() : fact.getOccurredFrom();
        if (eventAt == null) return new Assignment(null, "", "", "");
        ZonedDateTime local = eventAt.atZone(zone);
        LocalDate date = local.toLocalDate();
        int weekYear = date.get(ISO.weekBasedYear());
        int week = date.get(ISO.weekOfWeekBasedYear());
        return new Assignment(
            eventAt,
            date.toString(),
            String.format(Locale.ROOT, "%04d-W%02d", weekYear, week),
            YearMonth.from(local).toString()
        );
    }

    public PeriodRange resolve(TimelineGranularity granularity, String periodKey) {
        if (granularity == null) throw new IllegalArgumentException("Timeline granularity is required");
        String key = periodKey == null ? "" : periodKey.trim();
        if (granularity == TimelineGranularity.LIFECYCLE) {
            if (!"ALL".equals(key)) throw new IllegalArgumentException("Lifecycle period key must be ALL");
            return new PeriodRange("ALL", null, null);
        }
        try {
            if (granularity == TimelineGranularity.DAY && DAY_KEY.matcher(key).matches()) {
                LocalDate day = LocalDate.parse(key);
                return range(key, day, day.plusDays(1));
            }
            if (granularity == TimelineGranularity.MONTH && MONTH_KEY.matcher(key).matches()) {
                YearMonth month = YearMonth.parse(key);
                return range(key, month.atDay(1), month.plusMonths(1).atDay(1));
            }
            if (granularity == TimelineGranularity.WEEK) {
                Matcher matcher = WEEK_KEY.matcher(key);
                if (!matcher.matches()) throw new IllegalArgumentException("Invalid ISO week key");
                int weekYear = Integer.parseInt(matcher.group(1));
                int week = Integer.parseInt(matcher.group(2));
                LocalDate monday = LocalDate.of(weekYear, 1, 4)
                    .with(ISO.weekOfWeekBasedYear(), week)
                    .with(ISO.dayOfWeek(), DayOfWeek.MONDAY.getValue());
                String canonical = String.format(Locale.ROOT, "%04d-W%02d",
                    monday.get(ISO.weekBasedYear()), monday.get(ISO.weekOfWeekBasedYear()));
                if (!canonical.equals(key)) throw new IllegalArgumentException("Invalid ISO week key");
                return range(key, monday, monday.plusWeeks(1));
            }
        } catch (java.time.DateTimeException exception) {
            throw new IllegalArgumentException("Invalid timeline period key", exception);
        }
        throw new IllegalArgumentException("Invalid timeline period key");
    }

    public String zoneId() { return zone.getId(); }

    private PeriodRange range(String key, LocalDate start, LocalDate end) {
        return new PeriodRange(key, start.atStartOfDay(zone).toInstant(), end.atStartOfDay(zone).toInstant());
    }

    private static ZoneId resolveZone(String configuredZone) {
        return configuredZone == null || configuredZone.isBlank()
            ? ZoneId.systemDefault()
            : ZoneId.of(configuredZone.trim());
    }

    public record Assignment(Instant eventAt, String dayKey, String weekKey, String monthKey) {
    }

    public record PeriodRange(String periodKey, Instant startInclusive, Instant endExclusive) {
    }
}
