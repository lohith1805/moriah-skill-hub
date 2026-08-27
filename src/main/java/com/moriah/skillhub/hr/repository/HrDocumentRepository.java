package com.moriah.skillhub.hr.repository;

import com.moriah.skillhub.hr.entity.HrDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HrDocumentRepository extends JpaRepository<HrDocument, Long> {
}
