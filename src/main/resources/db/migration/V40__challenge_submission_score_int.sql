-- Fix: V38 declared challenge_submissions.score as TINYINT UNSIGNED, but the ChallengeSubmission
-- entity maps it as a Java Integer, which Hibernate schema-validation (on in the dev profile)
-- expects to be a plain INT — the mismatch aborts startup. Widen to INT; the 0-100 range is
-- still enforced by chk_challenge_submissions_score.
ALTER TABLE challenge_submissions
    MODIFY COLUMN score INT NULL;
