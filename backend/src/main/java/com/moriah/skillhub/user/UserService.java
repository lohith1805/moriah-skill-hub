package com.moriah.skillhub.user;

import com.moriah.skillhub.certificate.CertificateService;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.project.ProjectService;
import com.moriah.skillhub.sprint.TaskService;
import com.moriah.skillhub.user.dto.PortfolioResponse;
import com.moriah.skillhub.user.dto.UserProfileResponse;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserProfile;
import com.moriah.skillhub.user.repository.UserProfileRepository;
import com.moriah.skillhub.user.repository.UserRepository;
import com.moriah.skillhub.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-side of the {@code user/} package: the caller's own profile and the public portfolio.
 * Profile-lifecycle and completion logic live in {@link ProfileService}; this class composes
 * {@code User} + {@code UserProfile} into response records. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRoleRepository userRoleRepository;
    private final ProfileService profileService;
    private final TaskService taskService;
    private final ProjectService projectService;
    private final CertificateService certificateService;

    /** Not read-only — the first call for a user lazily creates their {@code UserProfile} row
     * (see {@link ProfileService#getOrCreateProfile}). */
    @Transactional
    public UserProfileResponse getMe(Long userId) {
        User user = requireUser(userId);
        UserProfile profile = profileService.getOrCreateProfile(user);
        return profileService.toResponse(user, profile);
    }

    /** Public — no {@code @PreAuthorize} ({@code /api/v1/portfolio/**} is on {@code
     * SecurityConfig}'s public-path list). build-plan.md feature 09: "name, title, skills,
     * completed projects, issued certificates only. Never email, phone, scores, or PIP status." —
     * {@link PortfolioResponse} physically cannot carry any of those fields. */
    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(String slug) {
        UserProfile profile = userProfileRepository.findByPortfolioSlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PORTFOLIO_NOT_FOUND, slug));
        User user = profile.getUser();

        List<Long> completedProjectIds = taskService.completedProjectIdsFor(user.getId());

        return new PortfolioResponse(
                user.getFullName(),
                profile.getCurrentTitle(),
                profile.getBio(),
                profile.getLocation(),
                profileService.fromJsonList(profile.getSkills(), String.class),
                projectService.findTitlesAndSlugs(completedProjectIds),
                certificateService.issuedCertificatesFor(user.getId()));
    }

    /** `/architect feature 10`: {@code BatchAllocationService} needs "every user holding role X"
     * (to broadcast a pending-allocation notification to every {@code TRAINER_PM}) without
     * touching {@code UserRoleRepository} directly — {@code batch/} is a sibling feature package,
     * so this is the legitimate cross-package "service interface, never the repository" call. */
    @Transactional(readOnly = true)
    public List<Long> findUserIdsByRole(RoleCode roleCode) {
        return userRoleRepository.findUserIdsByRoleCode(roleCode);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
    }
}
