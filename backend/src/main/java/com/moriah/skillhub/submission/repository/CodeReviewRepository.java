package com.moriah.skillhub.submission.repository;

import com.moriah.skillhub.submission.entity.CodeReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeReviewRepository extends JpaRepository<CodeReview, Long> {
}
