package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.BaMeetingResponse;
import com.moriah.skillhub.client.dto.CreateBaMeetingRequest;
import com.moriah.skillhub.client.dto.UpdateBaMeetingRequest;
import com.moriah.skillhub.client.entity.BaMeetingStatus;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * BA Meetings coordination (gap B1.14). Separate controller from {@link BaController} to keep
 * that one focused — same {@code /api/v1/ba} prefix, distinct {@code /meetings} sub-path.
 * BUSINESS_ANALYST/ADMIN, the {@code /ba/**} convention.
 */
@RestController
@RequestMapping("/api/v1/ba/meetings")
@RequiredArgsConstructor
@Tag(name = "BA")
public class BaMeetingController {

    private final BaMeetingService baMeetingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ANALYST','ADMIN')")
    @Operation(summary = "List meetings — optional status / clientProjectId filters")
    public ResponseEntity<ApiResponse<PageResponse<BaMeetingResponse>>> list(
            @RequestParam(required = false) BaMeetingStatus status,
            @RequestParam(required = false) Long clientProjectId,
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(baMeetingService.list(status, clientProjectId, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ANALYST','ADMIN')")
    @Operation(summary = "Schedule a meeting (starts SCHEDULED)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Meeting created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "clientProjectId does not exist")
    })
    public ResponseEntity<ApiResponse<BaMeetingResponse>> create(
            @Valid @RequestBody CreateBaMeetingRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(baMeetingService.create(request, callerUserId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('BUSINESS_ANALYST','ADMIN')")
    @Operation(summary = "Replace a meeting's fields — including status and minutes")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Meeting updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No meeting with this id, or clientProjectId does not exist")
    })
    public ResponseEntity<ApiResponse<BaMeetingResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateBaMeetingRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(baMeetingService.update(id, request, callerUserId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('BUSINESS_ANALYST','ADMIN')")
    @Operation(summary = "Cancel a meeting (status = CANCELLED) — never row-deletes")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Meeting cancelled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No meeting with this id")
    })
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long id, @CurrentUser Long callerUserId) {
        baMeetingService.cancel(id, callerUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
