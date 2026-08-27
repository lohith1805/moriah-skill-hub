package com.moriah.skillhub.client;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.client.dto.CreateResourceAllocationRequest;
import com.moriah.skillhub.client.dto.ResourceAllocationResponse;
import com.moriah.skillhub.client.entity.ClientProject;
import com.moriah.skillhub.client.entity.ResourceAllocation;
import com.moriah.skillhub.client.repository.ClientProjectRepository;
import com.moriah.skillhub.client.repository.ResourceAllocationRepository;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** build-plan.md feature 21: "Resource allocation maps students and batches to a client
 * project." Create-only — no update/list/delete endpoint exists in this feature's endpoint list
 * (same precedent {@code ResourceAllocationRepository}'s own Javadoc documents). Every FK is
 * validated with the standard {@code ResourceNotFoundException} pattern every other feature's
 * create endpoint already follows ({@code BatchRepository}/{@code UserRepository} injected
 * directly — both shared-kernel entities, same as {@code SprintService}/{@code
 * CertificateService}). */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceAllocationService {

    private final ResourceAllocationRepository resourceAllocationRepository;
    private final ClientProjectRepository clientProjectRepository;
    private final BatchRepository batchRepository;
    private final UserRepository userRepository;

    @Transactional
    public ResourceAllocationResponse create(CreateResourceAllocationRequest request) {
        ClientProject project = clientProjectRepository.findById(request.clientProjectId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLIENT_PROJECT_NOT_FOUND, request.clientProjectId()));
        Batch batch = batchRepository.findById(request.batchId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_NOT_FOUND, request.batchId()));
        User user = userRepository.findByUuid(request.userUuid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, request.userUuid()));

        ResourceAllocation allocation = new ResourceAllocation();
        allocation.setClientProject(project);
        allocation.setBatch(batch);
        allocation.setUser(user);
        allocation.setRoleInProject(request.roleInProject());
        allocation.setAllocatedDays(request.allocatedDays());
        allocation.setStoryPointsEstimate(request.storyPointsEstimate());
        allocation.setFromDate(request.fromDate());
        allocation.setToDate(request.toDate());
        resourceAllocationRepository.save(allocation);

        log.info("[ba/allocations] allocated user {} to client project {} on batch {}",
                user.getUuid(), project.getId(), batch.getId());

        return toResponse(allocation);
    }

    private ResourceAllocationResponse toResponse(ResourceAllocation allocation) {
        return new ResourceAllocationResponse(
                allocation.getId(),
                allocation.getClientProject().getId(),
                allocation.getBatch().getId(),
                allocation.getUser().getUuid(),
                allocation.getUser().getFullName(),
                allocation.getRoleInProject(),
                allocation.getAllocatedDays(),
                allocation.getStoryPointsEstimate(),
                allocation.getFromDate(),
                allocation.getToDate());
    }
}
