package com.moriah.skillhub.admin;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.payment.CouponAdminService;
import com.moriah.skillhub.payment.dto.CouponResponse;
import com.moriah.skillhub.payment.dto.CreateCouponRequest;
import com.moriah.skillhub.payment.dto.UpdateCouponRequest;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin coupon management (gap B1.12). {@code CouponService} + {@code Coupon} already existed and
 * are applied at checkout — this adds the missing CRUD surface. Routes through {@code
 * payment/CouponAdminService}, the same cross-module call {@code AdminPlanController} makes to
 * {@code EntitlementService}. Every method is {@code ADMIN}-only. The path variable is the
 * coupon {@code code}, its stable identifier (there is no {@code uuid} on {@code coupons}).
 */
@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class CouponAdminController {

    private final CouponAdminService couponAdminService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all coupons, newest first")
    public ResponseEntity<ApiResponse<PageResponse<CouponResponse>>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(couponAdminService.list(pageable)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a coupon")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Coupon created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "A coupon with this code already exists")
    })
    public ResponseEntity<ApiResponse<CouponResponse>> create(
            @Valid @RequestBody CreateCouponRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(couponAdminService.create(request, callerUserId)));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace a coupon's discount, validity window, cap and active flag")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Coupon updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No coupon with this code")
    })
    public ResponseEntity<ApiResponse<CouponResponse>> update(
            @PathVariable String code, @Valid @RequestBody UpdateCouponRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(couponAdminService.update(code, request, callerUserId)));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a coupon (active = false) — never row-deletes, so redemption history is kept")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Coupon deactivated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No coupon with this code")
    })
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable String code, @CurrentUser Long callerUserId) {

        couponAdminService.deactivate(code, callerUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
