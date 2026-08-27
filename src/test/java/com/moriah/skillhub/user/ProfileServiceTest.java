package com.moriah.skillhub.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.user.dto.EducationEntry;
import com.moriah.skillhub.user.dto.UpdateProfileRequest;
import com.moriah.skillhub.user.dto.UserProfileResponse;
import com.moriah.skillhub.user.dto.WorkExperienceEntry;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserProfile;
import com.moriah.skillhub.user.repository.UserProfileRepository;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A real {@link ObjectMapper} is used (not mocked) — the behaviour under test is genuinely "does
 * this round-trip through JSON correctly", which a mock can't prove.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserRepository userRepository;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(userProfileRepository, userRepository, new ObjectMapper().findAndRegisterModules());
    }

    private User user(Long id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setUuid("uuid-" + id);
        user.setFullName(fullName);
        user.setEmail(fullName.toLowerCase().replace(" ", ".") + "@example.com");
        return user;
    }

    @Test
    void getOrCreateProfile_existingProfile_returnsItWithoutCreatingANewOne() {
        User user = user(1L, "Ada Lovelace");
        UserProfile existing = new UserProfile();
        existing.setUser(user);
        existing.setPortfolioSlug("ada-lovelace-abcd1234");
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

        UserProfile result = profileService.getOrCreateProfile(user);

        assertThat(result).isSameAs(existing);
        verify(userProfileRepository, never()).save(any());
        verify(userProfileRepository, never()).existsByPortfolioSlug(anyString());
    }

    @Test
    void getOrCreateProfile_noExistingProfile_createsOneWithASlugifiedUniqueSlug() {
        User user = user(2L, "Grace Hopper");
        when(userProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(userProfileRepository.existsByPortfolioSlug(anyString())).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfile result = profileService.getOrCreateProfile(user);

        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getPortfolioSlug()).matches("grace-hopper-[0-9a-f]{"
                + (Constants.PORTFOLIO_SLUG_SUFFIX_BYTES * 2) + "}");
    }

    @Test
    void getOrCreateProfile_slugCollision_retriesUntilAnUnusedSlugIsFound() {
        User user = user(3L, "Katherine Johnson");
        when(userProfileRepository.findByUserId(3L)).thenReturn(Optional.empty());
        when(userProfileRepository.existsByPortfolioSlug(anyString())).thenReturn(true, true, false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfile result = profileService.getOrCreateProfile(user);

        assertThat(result.getPortfolioSlug()).startsWith("katherine-johnson-");
        verify(userProfileRepository, org.mockito.Mockito.times(3)).existsByPortfolioSlug(anyString());
    }

    @Test
    void recalculateCompletion_everyFieldFilled_is100PercentAndComplete() {
        User user = user(4L, "Margaret Hamilton");
        user.setGithubUsername("mhamilton");
        UserProfile profile = new UserProfile();
        profile.setBio("Software engineer.");
        profile.setLocation("Boston");
        profile.setCurrentTitle("Lead Engineer");
        profile.setExperienceLevel("SENIOR");
        profile.setYearsExperience(10);
        profile.setSkills("[\"Java\",\"Spring\"]");
        profile.setEducation("[{\"institution\":\"MIT\",\"degree\":\"BSc\"}]");
        profile.setWorkExperience("[{\"company\":\"NASA\",\"title\":\"Engineer\"}]");
        profile.setResumeKey("resumes/uuid-4/resume.pdf");

        profileService.recalculateCompletion(profile, user);

        assertThat(profile.getCompletionPercent()).isEqualTo(100);
        assertThat(profile.isComplete()).isTrue();
    }

    @Test
    void recalculateCompletion_noFieldsFilled_isZeroPercentAndNotComplete() {
        User user = user(5L, "Blank User");
        UserProfile profile = new UserProfile();

        profileService.recalculateCompletion(profile, user);

        assertThat(profile.getCompletionPercent()).isZero();
        assertThat(profile.isComplete()).isFalse();
    }

    @Test
    void recalculateCompletion_partialFields_roundsToNearestPercent() {
        User user = user(6L, "Partial User");
        UserProfile profile = new UserProfile();
        profile.setBio("Just a bio.");

        profileService.recalculateCompletion(profile, user);

        // 1 of Constants.PROFILE_COMPLETION_FIELD_COUNT filled.
        int expected = Math.round(100f / Constants.PROFILE_COMPLETION_FIELD_COUNT);
        assertThat(profile.getCompletionPercent()).isEqualTo(expected);
        assertThat(profile.isComplete()).isFalse();
    }

    @Test
    void updateProfile_setsGithubUsernameFallbackAndPersistsJsonFields() {
        User user = user(7L, "New User");
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setPortfolioSlug("new-user-11112222");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));

        UpdateProfileRequest request = new UpdateProfileRequest(
                "octocat",
                "Bio text",
                "Remote",
                "Backend Engineer",
                "MID",
                5,
                List.of("Java", "SQL"),
                List.of(new EducationEntry("MIT", "BSc", "CS", 2015, 2019)),
                List.of(new WorkExperienceEntry("Acme", "Engineer", LocalDate.of(2019, 1, 1), null, "Built things")));

        UserProfileResponse response = profileService.updateProfile(7L, request);

        assertThat(user.getGithubUsername()).isEqualTo("octocat");
        assertThat(response.githubUsername()).isEqualTo("octocat");
        assertThat(response.skills()).containsExactly("Java", "SQL");
        assertThat(response.education()).containsExactly(new EducationEntry("MIT", "BSc", "CS", 2015, 2019));
        assertThat(response.workExperience())
                .containsExactly(new WorkExperienceEntry("Acme", "Engineer", LocalDate.of(2019, 1, 1), null, "Built things"));
        assertThat(response.completionPercent()).isGreaterThan(0);
    }

    @Test
    void toResponse_emptyJsonFields_returnEmptyListsNeverNull() {
        User user = user(8L, "Empty User");
        UserProfile profile = new UserProfile();

        UserProfileResponse response = profileService.toResponse(user, profile);

        assertThat(response.skills()).isEmpty();
        assertThat(response.education()).isEmpty();
        assertThat(response.workExperience()).isEmpty();
        assertThat(response.hasResume()).isFalse();
    }
}
