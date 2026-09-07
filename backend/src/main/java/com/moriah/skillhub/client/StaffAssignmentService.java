package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.StaffWorkloadResponse;
import com.moriah.skillhub.client.entity.ClientProjectStatus;
import com.moriah.skillhub.client.repository.ClientProjectRepository;
import com.moriah.skillhub.client.repository.ClientProjectRepository.StaffOpenProjectCount;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserStatus;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Workload-aware round-robin routing for client projects — a BA the moment a project is
 * submitted, a developer the moment a BA first signs off a BRD/FRS (see
 * {@code ClientProjectService#create} and {@code RequirementDocumentApprovalService} for the two
 * call sites). "Least busy" rather than a blind rotation: picks whichever active candidate
 * currently has the fewest {@code SUBMITTED}/{@code IN_PROGRESS} client projects assigned to
 * them, tie-broken by user id for determinism — this is a strictly better form of round-robin
 * (it actually balances load instead of just cycling regardless of how fast each person clears
 * their queue).
 */
@Service
@RequiredArgsConstructor
public class StaffAssignmentService {

    private static final List<ClientProjectStatus> OPEN_STATUSES =
            List.of(ClientProjectStatus.SUBMITTED, ClientProjectStatus.IN_PROGRESS);

    private final UserRepository userRepository;
    private final ClientProjectRepository clientProjectRepository;

    /** {@code null} only when no active user holds {@code role} at all — the caller (project
     * submission, or a BA's first sign-off) leaves the assignment column {@code null} in that
     * case; an admin fills it in later from the Client Project Assignments screen. */
    @Transactional(readOnly = true)
    public Optional<User> pickLeastBusy(RoleCode role) {
        List<User> candidates = userRepository.search(UserStatus.ACTIVE, role, Pageable.unpaged()).getContent();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Long> openCounts = openCountsByUserId(role);
        return candidates.stream()
                .min(Comparator
                        .<User>comparingLong(u -> openCounts.getOrDefault(u.getId(), 0L))
                        .thenComparing(User::getId));
    }

    @Transactional(readOnly = true)
    public List<StaffWorkloadResponse> workload(RoleCode role) {
        List<User> candidates = userRepository.search(UserStatus.ACTIVE, role, Pageable.unpaged()).getContent();
        Map<Long, Long> openCounts = openCountsByUserId(role);
        return candidates.stream()
                .map(u -> {
                    int count = openCounts.getOrDefault(u.getId(), 0L).intValue();
                    return new StaffWorkloadResponse(u.getUuid(), u.getFullName(), count, workloadLabel(count));
                })
                .sorted(Comparator.comparingInt(StaffWorkloadResponse::openProjectCount))
                .toList();
    }

    private Map<Long, Long> openCountsByUserId(RoleCode role) {
        List<StaffOpenProjectCount> rows = role == RoleCode.BUSINESS_ANALYST
                ? clientProjectRepository.countOpenByAssignedBa(OPEN_STATUSES)
                : clientProjectRepository.countOpenByAssignedDeveloper(OPEN_STATUSES);
        return rows.stream().collect(java.util.stream.Collectors.toMap(
                StaffOpenProjectCount::getUserId, StaffOpenProjectCount::getOpenCount));
    }

    private static String workloadLabel(int openCount) {
        if (openCount <= Constants.STAFF_WORKLOAD_AVAILABLE_MAX) {
            return "Available";
        }
        if (openCount <= Constants.STAFF_WORKLOAD_BUSY_MAX) {
            return "Busy";
        }
        return "High workload";
    }
}
