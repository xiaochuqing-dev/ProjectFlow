package com.projectflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projectflow.entity.ProjectTimelineThemeFact;

public interface ProjectTimelineThemeFactRepository extends JpaRepository<ProjectTimelineThemeFact, UUID> {
    void deleteByThemeIdIn(List<UUID> themeIds);
    long countByThemeId(UUID themeId);

    @Query("""
        select relation.themeId as themeId, count(relation) as factCount
        from ProjectTimelineThemeFact relation where relation.themeId in :themeIds group by relation.themeId
        """)
    List<ThemeFactCountRow> countByThemeIds(@Param("themeIds") List<UUID> themeIds);

    @Query("select relation.factId from ProjectTimelineThemeFact relation where relation.themeId = :themeId")
    List<UUID> findFactIds(@Param("themeId") UUID themeId);

    interface ThemeFactCountRow {
        UUID getThemeId();
        long getFactCount();
    }
}
