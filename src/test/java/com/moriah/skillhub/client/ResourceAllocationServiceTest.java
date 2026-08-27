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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** build-plan.md feature 21: "Resource allocation maps students and batches to a client
 * project." Covers the happy path plus each of the three FK-validation branches ({@code
 * clientProjectId}/{@code batchId}/{@code userUuid}), matching this codebase's standard
 * create-endpoint {@code ResourceNotFoundException} pattern. */
@ExtendWith(MockitoExtension.class)
class ResourceAllocationServiceTest {

    @Mock
    private ResourceAllocationRepository resourceAllocationRepository;
    @Mock
    private ClientProjectRepository clientProjectRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ResourceAllocationService resourceAllocationService;

    private ClientProject project(long id) {
        ClientProject project = new ClientProject();
        project.setId(id);
        return project;
    }

    private Batch batch(long id) {
        Batch batch = new Batch();
        batch.setId(id);
        return batch;
    }

    private User user(long id, String uuid, String fullName) {
        User user = new User();
        user.setId(id);
        user.setUuid(uuid);
        user.setFullName(fullName);
        return user;
    }

    private CreateResourceAllocationRequest request(Long clientProjectId, Long batchId, String userUuid) {
        return new CreateResourceAllocationRequest(clientProjectId, batchId, userUuid, "Developer",
                20, 13, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    void create_happyPath_savesAndReturnsResponse() {
        when(clientProjectRepository.findById(10L)).thenReturn(Optional.of(project(10L)));
        when(batchRepository.findById(20L)).thenReturn(Optional.of(batch(20L)));
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(user(30L, "student-uuid", "Ada Lovelace")));
        when(resourceAllocationRepository.save(any(ResourceAllocation.class))).thenAnswer(inv -> inv.getArgument(0));

        ResourceAllocationResponse response = resourceAllocationService.create(request(10L, 20L, "student-uuid"));

        assertThat(response.clientProjectId()).isEqualTo(10L);
        assertThat(response.batchId()).isEqualTo(20L);
        assertThat(response.userUuid()).isEqualTo("student-uuid");
        assertThat(response.userFullName()).isEqualTo("Ada Lovelace");
        assertThat(response.roleInProject()).isEqualTo("Developer");
        assertThat(response.allocatedDays()).isEqualTo(20);
    }

    @Test
    void create_clientProjectNotFound_throwsResourceNotFound() {
        when(clientProjectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceAllocationService.create(request(999L, 20L, "student-uuid")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLIENT_PROJECT_NOT_FOUND);
        verify(resourceAllocationRepository, never()).save(any());
    }

    @Test
    void create_batchNotFound_throwsResourceNotFound() {
        when(clientProjectRepository.findById(10L)).thenReturn(Optional.of(project(10L)));
        when(batchRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceAllocationService.create(request(10L, 999L, "student-uuid")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BATCH_NOT_FOUND);
        verify(resourceAllocationRepository, never()).save(any());
    }

    @Test
    void create_userNotFound_throwsResourceNotFound() {
        when(clientProjectRepository.findById(10L)).thenReturn(Optional.of(project(10L)));
        when(batchRepository.findById(20L)).thenReturn(Optional.of(batch(20L)));
        when(userRepository.findByUuid("missing-uuid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceAllocationService.create(request(10L, 20L, "missing-uuid")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        verify(resourceAllocationRepository, never()).save(any());
    }
}
