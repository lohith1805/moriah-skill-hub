-- Flyway callback (reserved filename — not a versioned migration, runs after every successful
-- `migrate`, using the moriah_migrate connection). Grants moriah_app UPDATE, DELETE on every
-- table except audit_logs and flyway_schema_history, per architecture.md: "moriah_app... except
-- audit_logs, where it holds SELECT, INSERT only." SELECT/INSERT on everything (including
-- audit_logs) is granted schema-wide by docker/mysql-init/01-users.sql already.
--
-- flyway_schema_history is excluded for the same reason audit_logs is: the runtime app pool has
-- no legitimate reason to modify Flyway's own migration ledger, and information_schema.tables
-- would otherwise silently include it (caught in review, before it was ever exploitable —
-- nothing currently writes to it, but the grant existing at all was the gap).
--
-- Per-table, not schema-wide-then-revoke: MySQL privileges are additive across levels, so a
-- table-level REVOKE can never cancel a database-level GRANT — the earlier schema-wide-then-
-- revoke design silently didn't restrict audit_logs at all. See the recover-skill diagnosis in
-- progress-tracker.md.
--
-- Driven by information_schema, not a hardcoded table list: this makes the exclusion
-- self-maintaining as later migrations (V6 onward) add tables — nobody has to remember to
-- extend a list here. GRANT is idempotent (re-running changes nothing), so this is safe to run
-- on every startup, including ones where no new tables were added.
DROP PROCEDURE IF EXISTS grant_moriah_app_update_delete_except_audit_logs;

CREATE PROCEDURE grant_moriah_app_update_delete_except_audit_logs()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE tbl VARCHAR(64);
    DECLARE cur CURSOR FOR
        SELECT table_name
          FROM information_schema.tables
         WHERE table_schema = DATABASE()
           AND table_type = 'BASE TABLE'
           AND table_name NOT IN ('audit_logs', 'flyway_schema_history');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    grant_loop: LOOP
        FETCH cur INTO tbl;
        IF done THEN
            LEAVE grant_loop;
        END IF;
        SET @grant_sql = CONCAT('GRANT UPDATE, DELETE ON `', tbl, '` TO ''moriah_app''@''%''');
        PREPARE grant_stmt FROM @grant_sql;
        EXECUTE grant_stmt;
        DEALLOCATE PREPARE grant_stmt;
    END LOOP;
    CLOSE cur;
END;

CALL grant_moriah_app_update_delete_except_audit_logs();
DROP PROCEDURE grant_moriah_app_update_delete_except_audit_logs;
