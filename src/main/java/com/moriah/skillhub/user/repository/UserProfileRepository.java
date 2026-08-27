package com.moriah.skillhub.user.repository;

import com.moriah.skillhub.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserId(Long userId);

    Optional<UserProfile> findByPortfolioSlug(String portfolioSlug);

    boolean existsByPortfolioSlug(String portfolioSlug);
}
