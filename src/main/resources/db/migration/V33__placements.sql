-- Feature: client placement pipeline (frontend-integration Part A — the last mock module).
-- What happens AFTER a recruitment_request is APPROVED: a candidate moves through technical
-- rounds (client-driven), an HR round + document verification + offer creation (HR-driven),
-- then the client and student each sign, and HR marks the placement done.
--
-- One placement per approved recruitment_request. `stage` is an ordered enum (see
-- PlacementStage); regressions are rejected in the service, only REJECTED may be reached from
-- any non-terminal stage. `details` is a free-form JSON object — the FE's own loose bag of
-- stage-specific fields (interview link/notes/ratings, HR round schedule, document checklist,
-- offer text/CTC, sign timestamps, rejection reason) — round-tripped as a String by the service,
-- never modelled column-by-column here (matches how lesson_quiz options / user_profiles skills
-- are stored).

CREATE TABLE placements (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    recruitment_request_id BIGINT UNSIGNED NOT NULL,
    candidate_id           BIGINT UNSIGNED NOT NULL,
    client_id              BIGINT UNSIGNED NOT NULL,
    stage                  VARCHAR(30)     NOT NULL DEFAULT 'SHORTLISTED',
    details                JSON            NULL,
    created_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_placements_request UNIQUE (recruitment_request_id),
    CONSTRAINT fk_placements_request FOREIGN KEY (recruitment_request_id) REFERENCES recruitment_requests (id),
    CONSTRAINT fk_placements_candidate FOREIGN KEY (candidate_id) REFERENCES users (id),
    CONSTRAINT fk_placements_client FOREIGN KEY (client_id) REFERENCES users (id),
    INDEX idx_placements_candidate (candidate_id, stage),
    INDEX idx_placements_client (client_id, stage)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
