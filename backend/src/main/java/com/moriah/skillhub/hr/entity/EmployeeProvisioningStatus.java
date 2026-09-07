package com.moriah.skillhub.hr.entity;

/**
 * Where an {@code employees} row sits in the invite → onboarding pipeline, separate from the
 * employment-lifecycle {@link EmployeeStatus}.
 *
 * <ul>
 *   <li>{@code PENDING_HR} — auto-created on invite-accept with placeholder compensation
 *       ({@code StaffEmployeeProvisioningListener}); shows up in HR's "Pending Employee Records"
 *       until HR fills the real fields and approves it.</li>
 *   <li>{@code CONFIRMED} — HR has reviewed and approved (or the record was created directly by
 *       HR via {@code POST /hr/employees}, which is itself the review).</li>
 * </ul>
 */
public enum EmployeeProvisioningStatus {
    PENDING_HR,
    CONFIRMED
}
