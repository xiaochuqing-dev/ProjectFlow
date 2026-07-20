package com.projectflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectTimelineTheme;

public interface ProjectTimelineThemeRepository extends JpaRepository<ProjectTimelineTheme, UUID> {
    List<ProjectTimelineTheme> findBySummaryIdOrderBySortOrderAsc(UUID summaryId);
    List<ProjectTimelineTheme> findBySummaryIdInOrderBySortOrderAsc(List<UUID> summaryIds);
    Optional<ProjectTimelineTheme> findByIdAndProjectId(UUID id, UUID projectId);
    List<ProjectTimelineTheme> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);
    void deleteBySummaryId(UUID summaryId);

    @Query("""
        select theme.summaryId as summaryId, count(theme) as themeCount
        from ProjectTimelineTheme theme where theme.summaryId in :summaryIds group by theme.summaryId
        """)
    List<ThemeCountRow> countBySummaryIds(@Param("summaryIds") List<UUID> summaryIds);

    interface ThemeCountRow {
        UUID getSummaryId();
        long getThemeCount();
    }
}
