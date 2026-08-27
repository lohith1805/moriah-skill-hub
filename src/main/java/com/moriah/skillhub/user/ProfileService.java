package com.moriah.skillhub.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.user.dto.EducationEntry;
import com.moriah.skillhub.user.dto.UpdateProfileRequest;
import com.moriah.skillhub.user.dto.UserProfileResponse;
import com.moriah.skillhub.user.dto.WorkExperienceEntry;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserProfile;
import com.moriah.skillhub.user.repository.UserProfileRepository;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Owns {@code UserProfile}'s lifecycle — lazy get-or-create (build-plan.md feature 09 gives no
 * "created at registration" instruction, unlike {@code user_roles}), the profile-fields half of
 * {@code PUT /api/v1/users/me/profile}, and completion-percent recalculation. {@code UserService}
 * (self/public reads) and {@code ResumeService} (upload/presign) both call into this rather than
 * touching {@code UserProfile} directly, so "how a profile gets created" and "how completion gets
 * computed" stay in one place.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_SLUG_ATTEMPTS = 5;
    private static final int SLUG_BASE_MAX_LENGTH = 100; // leaves headroom under VARCHAR(150)

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public UserProfile getOrCreateProfile(User user) {
        return userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    UserProfile profile = new UserProfile();
                    profile.setUser(user);
                    profile.setPortfolioSlug(generateUniqueSlug(user.getFullName()));
                    return userProfileRepository.save(profile);
                });
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
        UserProfile profile = getOrCreateProfile(user);

        if (request.githubUsername() != null) {
            user.setGithubUsername(request.githubUsername());
        }
        profile.setBio(request.bio());
        profile.setLocation(request.location());
        profile.setCurrentTitle(request.currentTitle());
        profile.setExperienceLevel(request.experienceLevel());
        profile.setYearsExperience(request.yearsExperience());
        profile.setSkills(toJson(request.skills()));
        profile.setEducation(toJson(request.education()));
        profile.setWorkExperience(toJson(request.workExperience()));

        recalculateCompletion(profile, user);
        return toResponse(user, profile);
    }

    /** Called after every mutation to {@code UserProfile} or to {@code user.githubUsername} —
     * {@code ResumeService.upload} calls this too, since a resume counts toward completion.
     * {@code is_complete} is derived here, never client-supplied (build-plan.md feature 09). */
    public void recalculateCompletion(UserProfile profile, User user) {
        int filled = 0;
        if (isNotBlank(profile.getBio())) filled++;
        if (isNotBlank(profile.getLocation())) filled++;
        if (isNotBlank(profile.getCurrentTitle())) filled++;
        if (isNotBlank(profile.getExperienceLevel())) filled++;
        if (profile.getYearsExperience() != null) filled++;
        if (!fromJsonList(profile.getSkills(), String.class).isEmpty()) filled++;
        if (!fromJsonList(profile.getEducation(), EducationEntry.class).isEmpty()) filled++;
        if (!fromJsonList(profile.getWorkExperience(), WorkExperienceEntry.class).isEmpty()) filled++;
        if (isNotBlank(profile.getResumeKey())) filled++;
        if (isNotBlank(user.getGithubUsername())) filled++;

        int percent = Math.round(filled * 100f / Constants.PROFILE_COMPLETION_FIELD_COUNT);
        profile.setCompletionPercent(percent);
        profile.setComplete(percent == 100);
    }

    public UserProfileResponse toResponse(User user, UserProfile profile) {
        return new UserProfileResponse(
                user.getUuid(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getGithubUsername(),
                user.getLinkedinUrl(),
                profile.getBio(),
                profile.getLocation(),
                profile.getCurrentTitle(),
                profile.getExperienceLevel(),
                profile.getYearsExperience(),
                fromJsonList(profile.getSkills(), String.class),
                fromJsonList(profile.getEducation(), EducationEntry.class),
                fromJsonList(profile.getWorkExperience(), WorkExperienceEntry.class),
                profile.getResumeKey() != null,
                profile.getPortfolioSlug(),
                profile.isComplete(),
                profile.getCompletionPercent());
    }

    /** Package-visible — {@code UserService.getPortfolio} reuses this to parse {@code skills}
     * for the public portfolio view rather than duplicating the Jackson plumbing. */
    <T> List<T> fromJsonList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return objectMapper.readValue(json, listType);
        } catch (JsonProcessingException e) {
            log.warn("[user/profile] failed to parse stored {} JSON, treating as empty", elementType.getSimpleName(), e);
            return List.of();
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("[user/profile] failed to serialize profile field, storing null", e);
            return null;
        }
    }

    /** build-plan.md feature 09: "portfolio_slug unique, name plus random suffix." Retries on
     * collision rather than trusting a single random draw — cheap insurance since the unique
     * index is the real guarantee either way. */
    private String generateUniqueSlug(String fullName) {
        String base = slugify(fullName);
        for (int attempt = 0; attempt < MAX_SLUG_ATTEMPTS; attempt++) {
            String candidate = base + "-" + randomSuffix();
            if (!userProfileRepository.existsByPortfolioSlug(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique portfolio slug after " + MAX_SLUG_ATTEMPTS + " attempts");
    }

    private String slugify(String fullName) {
        String slug = fullName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            slug = "user";
        }
        return slug.length() > SLUG_BASE_MAX_LENGTH ? slug.substring(0, SLUG_BASE_MAX_LENGTH) : slug;
    }

    private String randomSuffix() {
        byte[] bytes = new byte[Constants.PORTFOLIO_SLUG_SUFFIX_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
