package com.moriah.skillhub.user.repository;

import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUuid(String uuid);

    /** Audit 2026-08-31 (H7): lightweight projection for the per-request auth check in
     * {@code JwtAuthFilter} — avoids materialising the full {@code User} entity on every call. */
    Optional<AuthUserView> findAuthViewByUuid(String uuid);

    boolean existsByEmail(String email);

    /** {@code users.phone} is {@code UNIQUE} (V1) — self-register flows pre-check it so a
     * duplicate surfaces as a clean {@code PHONE_ALREADY_REGISTERED}, not a raw constraint 409. */
    boolean existsByPhone(String phone);

    /** feature 22: {@code GET /admin/users?role=&status=} — both filters optional. An {@code
     * EXISTS} subquery against {@code UserRole}/{@code Role} rather than a {@code JOIN}, so a
     * multi-role user contributes exactly one row regardless of {@code role} being supplied
     * (a {@code LEFT JOIN} would fan out one row per role and need {@code DISTINCT}, which then
     * complicates the {@code Page} count query — see {@code LeadRepository.search}'s own explicit
     * {@code countQuery} for the sibling pattern this mirrors). */
    @Query(value = """
            SELECT u FROM User u
            WHERE (:status IS NULL OR u.status = :status)
              AND (:role IS NULL OR EXISTS (
                    SELECT 1 FROM UserRole ur JOIN Role r ON r.id = ur.id.roleId
                     WHERE ur.id.userId = u.id AND r.code = :role))
            """,
            countQuery = """
            SELECT COUNT(u) FROM User u
            WHERE (:status IS NULL OR u.status = :status)
              AND (:role IS NULL OR EXISTS (
                    SELECT 1 FROM UserRole ur JOIN Role r ON r.id = ur.id.roleId
                     WHERE ur.id.userId = u.id AND r.code = :role))
            """)
    Page<User> search(@Param("status") UserStatus status, @Param("role") RoleCode role, Pageable pageable);
}
