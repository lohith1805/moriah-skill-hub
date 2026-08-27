package com.moriah.skillhub.crm.repository;

import com.moriah.skillhub.crm.entity.LeadActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, Long> {

    List<LeadActivity> findByLeadIdOrderByOccurredAtDesc(Long leadId);
}
