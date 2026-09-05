package com.moriah.skillhub.client.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Composite key for {@link BaMeetingAttendee} — same shape as {@code UserRoleId}/{@code
 * LeadCampaignRecipientId}. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class BaMeetingAttendeeId implements Serializable {

    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "user_id")
    private Long userId;
}
