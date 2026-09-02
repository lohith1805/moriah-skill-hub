package com.moriah.skillhub.auth.repository;

import com.moriah.skillhub.auth.entity.StaffInviteToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface StaffInviteTokenRepository extends JpaRepository<StaffInviteToken, Long> {

    Optional<StaffInviteToken> findByTokenHash(String tokenHash);

    /** Burn every still-open invite for this user in one statement — used before issuing a fresh
     * one on resend, so an earlier link can't also be redeemed. */
    @Modifying
    @Query("UPDATE StaffInviteToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.usedAt IS NULL")
    int markAllUnusedAsUsedForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
