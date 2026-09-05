package com.moriah.skillhub.client.repository;

import com.moriah.skillhub.client.entity.BaMeetingAttendee;
import com.moriah.skillhub.client.entity.BaMeetingAttendeeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BaMeetingAttendeeRepository extends JpaRepository<BaMeetingAttendee, BaMeetingAttendeeId> {

    List<BaMeetingAttendee> findByIdMeetingId(Long meetingId);

    /** {@code BaMeetingService#list}'s batched per-page attendee lookup — one flat query for a
     * whole page of meetings instead of one {@link #findByIdMeetingId} call per row
     * (code-standards.md "N+1 Prevention"). */
    List<BaMeetingAttendee> findByIdMeetingIdIn(Collection<Long> meetingIds);

    /** {@code BaMeetingService#myMeetings} — every meeting the caller is invited to, regardless of
     * who scheduled it or which role they hold. */
    List<BaMeetingAttendee> findByIdUserId(Long userId);

    /** {@code BaMeetingService#update}'s wholesale replace — same delete-then-insert idiom {@code
     * LeadCampaignService#update} uses for {@code lead_campaign_recipients}. */
    void deleteByIdMeetingId(Long meetingId);

    @Query("SELECT a.id.userId FROM BaMeetingAttendee a WHERE a.id.meetingId = :meetingId")
    List<Long> findUserIdsByMeetingId(@Param("meetingId") Long meetingId);
}
