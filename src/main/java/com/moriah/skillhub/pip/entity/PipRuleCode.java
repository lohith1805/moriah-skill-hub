package com.moriah.skillhub.pip.entity;

/** The six fixed evaluators (build-plan.md feature 17) — declaration order is the default
 * severity tie-break {@code PipEvaluationService} falls back to when two rules share the same
 * current severity in {@code pip_rules} (its own evaluation order is otherwise severity-driven,
 * highest first). Not an open-ended catalogue: each value has exactly one {@code
 * pip.engine.PipRuleEvaluator} implementation, and V11's {@code chk_pip_rules_code} CHECK
 * constraint mirrors this exact set.
 * <p>
 * {@code milestoneTitle} is the one auto-generated {@code pip_milestones} row a trigger creates
 * for that rule — colocated with the rule code itself (a {@code /review} finding against an
 * earlier draft that instead buried this mapping in a {@code switch} inside {@code
 * PipEvaluationService}, one file away from the enum whose values it exhaustively switches on). */
public enum PipRuleCode {
    ATTENDANCE_LOW("Attend every standup for the remainder of the PIP window"),
    PROJECT_DELAY("Clear all overdue committed tasks"),
    ASSIGNMENT_MISSED("Submit the next two weekly assignments on time"),
    QUIZ_FAILURE("Retake and pass the assessment that triggered this PIP"),
    REVIEW_FAILED("Earn a satisfactory weekly review"),
    TASK_ABANDONED("Resume active work and log visible progress");

    private final String milestoneTitle;

    PipRuleCode(String milestoneTitle) {
        this.milestoneTitle = milestoneTitle;
    }

    public String milestoneTitle() {
        return milestoneTitle;
    }
}
