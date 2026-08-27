package com.moriah.skillhub.hr;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.hr.dto.IssueLetterRequest;
import com.moriah.skillhub.hr.dto.LetterResponse;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.EmployeeStatus;
import com.moriah.skillhub.hr.entity.LetterType;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrLetterServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private BatchService batchService;
    @Mock
    private StorageService storageService;
    @Mock
    private AuditLogService auditLogService;

    // Built per-test, not as a field initializer — a field initializer runs during construction,
    // before MockitoExtension's beforeEach injects @Mock fields, leaving them null.
    private HrLetterService service() {
        return new HrLetterService(userRepository, employeeRepository, batchService, storageService, auditLogService);
    }

    private User user(long id, String uuid) {
        User user = new User();
        user.setId(id);
        user.setUuid(uuid);
        user.setFullName("Target " + id);
        return user;
    }

    @Test
    void issue_offerLetter_noEligibilityCheckRequired() throws Exception {
        when(userRepository.findByUuid("target-uuid")).thenReturn(Optional.of(user(1L, "target-uuid")));
        when(storageService.uploadTrusted(anyString(), any(), anyString())).thenReturn("hr-letters/target-uuid/OFFER-x.pdf");
        when(storageService.presignedGetUrl(eq("caller-uuid"), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        LetterResponse response = service().issue(LetterType.OFFER, new IssueLetterRequest("target-uuid"), "caller-uuid", 9L);

        assertThat(response.downloadUrl()).isEqualTo("https://s3.example.com/x");
    }

    @Test
    void issue_experienceLetter_graduatedStudent_succeeds() throws Exception {
        when(userRepository.findByUuid("target-uuid")).thenReturn(Optional.of(user(1L, "target-uuid")));
        when(batchService.hasGraduated(1L)).thenReturn(true);
        when(storageService.uploadTrusted(anyString(), any(), anyString())).thenReturn("hr-letters/target-uuid/EXPERIENCE-x.pdf");
        when(storageService.presignedGetUrl(eq("caller-uuid"), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        LetterResponse response = service().issue(LetterType.EXPERIENCE, new IssueLetterRequest("target-uuid"), "caller-uuid", 9L);

        assertThat(response.downloadUrl()).isNotBlank();
    }

    @Test
    void issue_experienceLetter_cleanlyExitedEmployee_succeeds() throws Exception {
        when(userRepository.findByUuid("target-uuid")).thenReturn(Optional.of(user(1L, "target-uuid")));
        when(batchService.hasGraduated(1L)).thenReturn(false);
        Employee employee = new Employee();
        employee.setStatus(EmployeeStatus.EXITED);
        when(employeeRepository.findByUserId(1L)).thenReturn(Optional.of(employee));
        when(storageService.uploadTrusted(anyString(), any(), anyString())).thenReturn("hr-letters/target-uuid/RELIEVING-x.pdf");
        when(storageService.presignedGetUrl(eq("caller-uuid"), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        LetterResponse response = service().issue(LetterType.RELIEVING, new IssueLetterRequest("target-uuid"), "caller-uuid", 9L);

        assertThat(response.downloadUrl()).isNotBlank();
    }

    @Test
    void issue_experienceLetter_terminatedEmployee_throwsNotEligible() {
        when(userRepository.findByUuid("target-uuid")).thenReturn(Optional.of(user(1L, "target-uuid")));
        when(batchService.hasGraduated(1L)).thenReturn(false);
        Employee employee = new Employee();
        employee.setStatus(EmployeeStatus.TERMINATED);
        when(employeeRepository.findByUserId(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service().issue(LetterType.EXPERIENCE, new IssueLetterRequest("target-uuid"), "caller-uuid", 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LETTER_NOT_ELIGIBLE);
    }

    @Test
    void issue_experienceLetter_neitherGraduatedNorEmployee_throwsNotEligible() {
        when(userRepository.findByUuid("target-uuid")).thenReturn(Optional.of(user(1L, "target-uuid")));
        when(batchService.hasGraduated(1L)).thenReturn(false);
        when(employeeRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().issue(LetterType.RELIEVING, new IssueLetterRequest("target-uuid"), "caller-uuid", 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LETTER_NOT_ELIGIBLE);
    }

    @Test
    void issue_unknownUser_throwsNotFound() {
        when(userRepository.findByUuid("no-such-uuid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().issue(LetterType.OFFER, new IssueLetterRequest("no-such-uuid"), "caller-uuid", 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
