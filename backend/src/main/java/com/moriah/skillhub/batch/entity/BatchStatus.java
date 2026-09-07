package com.moriah.skillhub.batch.entity;

/** Matches V6's {@code chk_batches_status} CHECK constraint exactly. */
public enum BatchStatus {
    PLANNED,
    ACTIVE,
    COMPLETED,
    CANCELLED
}
