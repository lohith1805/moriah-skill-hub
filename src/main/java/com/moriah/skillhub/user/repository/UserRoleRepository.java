package com.moriah.skillhub.user.repository;

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
