package com.moriah.skillhub.client.dto;

import com.moriah.skillhub.client.entity.ClientStatus;

/** {@code userUuid} is {@code null} when no portal login was provisioned — never {@code
 * users.id} (architecture.md invariant: "never expose users.id"). */
public record ClientResponse(
        Long id,
        String companyName,
        String contactPerson,
        String email,
        String phone,
        String industry,
        String userUuid,
        ClientStatus status
) {
}
