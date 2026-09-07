package com.moriah.skillhub.hr;

import com.moriah.skillhub.auth.event.StaffInviteAcceptedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns "a staff member accepted their invite" into "they have an {@code employees} row". Runs
 * AFTER_COMMIT so a failure here can never roll back the account activation — if provisioning
 * fails, it is logged and HR can still add the record by hand. {@link EmployeeService#autoProvisionForStaff}
 * is idempotent, so a redelivered event is harmless.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaffEmployeeProvisioningListener {

    private final EmployeeService employeeService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStaffInviteAccepted(StaffInviteAcceptedEvent event) {
        try {
            employeeService.autoProvisionForStaff(event.userId());
        } catch (Exception e) {
            log.error("[hr] auto-provisioning an employees row failed for user {} after invite accept "
                    + "— HR can add it manually on the Employees screen", event.userId(), e);
        }
    }
}
