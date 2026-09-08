package com.moriah.skillhub.permission.repository;

import com.moriah.skillhub.permission.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleAscCodeAsc();
}
