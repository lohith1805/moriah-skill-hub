package com.moriah.skillhub.client.dto;

import com.moriah.skillhub.client.entity.RequirementDocumentStatus;
import com.moriah.skillhub.client.entity.RequirementDocumentType;

/** {@code approvedByUuid}/{@code approvedByFullName} are {@code null} until {@code PUT
 * /ba/documents/{id}/approve} is called. */
public record RequirementDocumentResponse(
        Long id,
        Long clientProjectId,
        RequirementDocumentType docType,
        String title,
        int version,
        RequirementDocumentStatus status,
        String authoredByUuid,
        String authoredByFullName,
        String approvedByUuid,
        String approvedByFullName
) {
}
