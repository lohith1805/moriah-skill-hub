package com.moriah.skillhub.hr.entity;

/** {@code DRAFT}/{@code PAID} are schema-valid but not reachable through any endpoint in
 * build-plan.md's feature 19 list — {@code POST /hr/payroll/generate} computes final numbers and
 * renders the payslip in one step, so it produces {@code FINALISED} directly. Both extra values
 * are left in place for a later admin workflow (marking a payslip actually paid) that isn't part
 * of this feature's scope, the same "table exists in schema, not fully API-managed" treatment
 * {@code sales_targets} got in feature 18. */
public enum PayrollStatus {
    DRAFT,
    FINALISED,
    PAID
}
