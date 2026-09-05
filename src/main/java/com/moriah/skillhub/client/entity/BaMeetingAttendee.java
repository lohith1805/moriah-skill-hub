package com.moriah.skillhub.client.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A pure join row — one person invited to a {@link BaMeeting} ("Client Pre-Project Discussions"
 * in the frontend). No {@code @ManyToOne} associations: same "a join row is a fact, not a
 * navigable entity graph" reasoning {@code UserRole}/{@code LeadCampaignRecipient} already
 * establish — {@code BaMeetingService} batches the actual name/email lookup via {@code
 * UserRepository.findAllById}. */
@Entity
@Table(name = "ba_meeting_attendees")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BaMeetingAttendee {

    @EmbeddedId
    private BaMeetingAttendeeId id;

    public BaMeetingAttendee(Long meetingId, Long userId) {
        this.id = new BaMeetingAttendeeId(meetingId, userId);
    }
}
