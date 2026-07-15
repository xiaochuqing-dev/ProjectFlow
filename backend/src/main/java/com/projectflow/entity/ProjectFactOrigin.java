package com.projectflow.entity;

public enum ProjectFactOrigin {
    INCREMENTAL_SCAN,
    HISTORY_BACKFILL,
    LEGACY_SEGMENT_MIGRATION,
    LEGACY_SEDIMENT_MIGRATION
}
