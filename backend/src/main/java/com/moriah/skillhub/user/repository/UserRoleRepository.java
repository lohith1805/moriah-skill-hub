package com.moriah.skillhub.user.repository;

import com.moriah.skillhub.user.dto.UserRoleCodeProjection;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.UserRole;
import com.moriah.skillhub.user.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("""
        SELECT r.code FROM UserRole ur
        JOIN Role r ON r.id = ur.id.roleId
        WHERE ur.id.userId = :userId
        """)
    List<RoleCode> findRoleCodesByUserId(@Param("userId") Long userId);

    /** feature 22: {@code AdminUserService.list}'s batched per-page role lookup — one query for a
     * whole {@code Page<User>} instead of one {@link #findRoleCodesByUserId} call per row
     * (code-standards.md "no repository call inside a loop"). */
    @Query("""
        SELECT new com.moriah.skillhub.user.dto.UserRoleCodeProjection(ur.id.userId, r.code)
        FROM UserRole ur JOIN Role r ON r.id = ur.id.roleId
        WHERE ur.id.userId IN :userIds
        """)
    List<UserRoleCodeProjection> findRoleCodesByUserIds(@Param("userIds") List<Long> userIds);

    /** `/architect feature 10`: {@code BatchAllocationService} broadcasts a pending-allocation
     * notification to every {@code TRAINER_PM} — the reverse direction of {@link
     * #findRoleCodesByUserId}. */
    @Query("""
        SELECT ur.id.userId FROM UserRole ur
        JOIN Role r ON r.id = ur.id.roleId
        WHERE r.code = :roleCode
        """)
    List<Long> findUserIdsByRoleCode(@Param("roleCode") RoleCode roleCode);

    void deleteByIdUserId(Long userId);
}
