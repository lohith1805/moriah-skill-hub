package com.moriah.skillhub.client.repository;

import com.moriah.skillhub.client.entity.ResourceAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

/** No update/list/delete endpoint exists in build-plan.md's feature 21 endpoint list — {@code
 * ResourceAllocationService#create} only ever calls {@code save}, inherited from {@code
 * JpaRepository} with nothing else needed. */
public interface ResourceAllocationRepository extends JpaRepository<ResourceAllocation, Long> {
}
