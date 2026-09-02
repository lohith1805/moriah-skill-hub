package com.moriah.skillhub.auth;

import com.moriah.skillhub.auth.dto.AcceptInviteRequest;
import com.moriah.skillhub.auth.dto.ClientRegisterRequest;
import com.moriah.skillhub.auth.dto.ForgotPasswordRequest;
import com.moriah.skillhub.auth.dto.LoginRequest;
import com.moriah.skillhub.auth.dto.LoginResponse;
import com.moriah.skillhub.auth.dto.LogoutRequest;
import com.moriah.skillhub.auth.dto.RefreshRequest;
import com.moriah.skillhub.auth.dto.RegisterRequest;
import com.moriah.skillhub.auth.dto.RegisterResponse;
import com.moriah.skillhub.auth.dto.ResetPasswordRequest;
import com.moriah.skillhub.auth.dto.TokenPairResponse;
import com.moriah.skillhub.auth.dto.TwoFactorDisableRequest;
import com.moriah.skillhub.auth.dto.TwoFactorEnableRequest;
import com.moriah.skillhub.auth.dto.TwoFactorEnableResponse;
import com.moriah.skillhub.auth.dto.TwoFactorVerifyRequest;
import com.moriah.skillhub.auth.dto.TwoFactorVerifyResponse;
import com.moriah.skillhub.auth.dto.VerifyEmailRequest;
import com.moriah.skillhub.client.ClientRegistrationService;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.security.ClientIpResolver;
import com.moriah.skillhub.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint here is public at the filter level (SecurityConfig, architecture.md "Public
 * endpoints") — no {@code @PreAuthorize} on any method, per code-standards.md's stated exception
 * for explicitly public endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final TwoFactorService twoFactorService;
    private final ClientIpResolver clientIpResolver;
    private final ClientRegistrationService clientRegistrationService;

    @PostMapping("/register")
    @Operation(summary = "Register a new student account")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/register/client")
    @Operation(summary = "Corporate-client self-registration — creates a PENDING_APPROVAL account that "
            + "cannot log in until an ADMIN approves it at POST /api/v1/admin/client-requests/{uuid}/approve")
    public ResponseEntity<ApiResponse<RegisterResponse>> registerClient(
            @Valid @RequestBody ClientRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(clientRegistrationService.register(request)));
    }

    @PostMapping("/accept-invite")
    @Operation(summary = "Redeem a staff accept-invite link — sets the password, activates the account "
            + "(INVITED -> ACTIVE) and logs in. Returns a 2FA challenge instead of tokens for an "
            + "invited ADMIN / HR_MANAGER, exactly like a normal first login.")
    public ResponseEntity<ApiResponse<LoginResponse>> acceptInvite(
            @Valid @RequestBody AcceptInviteRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = authService.acceptInvite(request, userAgent(httpRequest), clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password. If 2FA is enabled, returns a challenge "
            + "token instead of a token pair — exchange it at POST /2fa/verify.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, userAgent(httpRequest), clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair; rotates the refresh token")
    public ResponseEntity<ApiResponse<TokenPairResponse>> refresh(
            @Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        TokenPairResponse response = authService.refresh(request, userAgent(httpRequest), clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke one refresh token and its paired access token")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request, HttpServletRequest httpRequest) {
        authService.logout(request, httpRequest.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Revoke every session for the user identified by this refresh token")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @Valid @RequestBody LogoutRequest request, HttpServletRequest httpRequest) {
        authService.logoutAll(request, httpRequest.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify an email address with the token from the verification link")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Request a password reset link — always responds the same way regardless of whether the email exists")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Reset a password with the token from the reset link")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/2fa/enable")
    @Operation(summary = "Start 2FA setup — generates a secret; confirm it via POST /2fa/verify "
            + "before it takes effect. Send {} for an already-logged-in caller voluntarily "
            + "enabling 2FA; send {\"challengeToken\": \"...\"} for the mandatory-2FA setup path "
            + "(LoginResponse.twoFactorSetupRequired = true), where there is no access token yet "
            + "to authenticate this call with")
    public ResponseEntity<ApiResponse<TwoFactorEnableResponse>> enableTwoFactor(
            @RequestBody TwoFactorEnableRequest request,
            @CurrentUser Long callerUserId) {
        TwoFactorEnableResponse response = twoFactorService.enable(callerUserId, request.challengeToken());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/2fa/verify")
    @Operation(summary = "Confirms 2FA setup (no challengeToken, uses the caller's access token) "
            + "or completes a 2FA-gated login (challengeToken from LoginResponse)")
    public ResponseEntity<ApiResponse<TwoFactorVerifyResponse>> verifyTwoFactor(
            @Valid @RequestBody TwoFactorVerifyRequest request,
            @CurrentUser Long callerUserId,
            HttpServletRequest httpRequest) {
        if (request.challengeToken() != null) {
            TokenPairResponse tokens = authService.completeTwoFactorLogin(
                    request, userAgent(httpRequest), clientIp(httpRequest));
            return ResponseEntity.ok(ApiResponse.success(TwoFactorVerifyResponse.loginCompleted(tokens)));
        }
        twoFactorService.confirmSetup(callerUserId, request.totpCode());
        return ResponseEntity.ok(ApiResponse.success(TwoFactorVerifyResponse.setupConfirmed()));
    }

    @PostMapping("/2fa/disable")
    @Operation(summary = "Disable 2FA — requires a currently-valid code, not just an authenticated call")
    public ResponseEntity<ApiResponse<Void>> disableTwoFactor(
            @Valid @RequestBody TwoFactorDisableRequest request, @CurrentUser Long callerUserId) {
        twoFactorService.disable(callerUserId, request.totpCode());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
