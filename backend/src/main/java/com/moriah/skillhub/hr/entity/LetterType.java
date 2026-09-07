package com.moriah.skillhub.hr.entity;

/** {@code EXPERIENCE}/{@code RELIEVING} require the target to be {@code GRADUATED} (student
 * track) or a clean {@code EXITED} employee (staff track) — never {@code TERMINATED} — enforced
 * by {@code HrLetterService}. {@code OFFER}/{@code INTERNSHIP} carry no such restriction (issued
 * before any batch/employee lifecycle status is even meaningful yet). */
public enum LetterType {
    OFFER,
    INTERNSHIP,
    EXPERIENCE,
    RELIEVING
}
