package com.moriah.skillhub.client.entity;

/** Mirrors {@code chk_ba_meetings_status} in {@code V25__ba_meetings.sql}. {@code DELETE} moves a
 * meeting to {@code CANCELLED} rather than row-deleting it. */
public enum BaMeetingStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED
}
