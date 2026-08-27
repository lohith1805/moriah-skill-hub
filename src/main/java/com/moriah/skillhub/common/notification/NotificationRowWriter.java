package com.moriah.skillhub.common.notification;

import com.moriah.skillhub.common.notification.entity.Notification;
import com.moriah.skillhub.common.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ONLY the transactional {@code notifications} row write — deliberately a bean separate from
 * {@link NotificationWriter}, for the exact same self-invocation reason documented on that
 * class: {@link NotificationWriter#write}/{@link NotificationWriter#writeInNewTransaction} call
 * these methods through this bean's own injected reference (a genuine, correctly-proxied
 * cross-bean call), not {@code this.save(...)}.
 * <p>
 * Found the hard way, a second time in this same feature: the very first version of {@code
 * NotificationWriter} did the Redis push from inside its own {@code @Transactional} method body,
 * after {@code save()} but before the method itself returned — meaning the push happened before
 * Spring's transactional proxy had actually committed the surrounding transaction. Under light
 * load the race window was too small to ever observe; under the full test suite's heavier
 * concurrent load, {@code NotificationWorker} — a separate thread, separate connection — would
 * routinely pop the just-pushed message and call {@code findById} before the row was durably
 * committed, logging "notification N no longer exists, dropping" for nearly every notification
 * enqueued. Splitting the save into its own bean lets {@link NotificationWriter} call it, get
 * back control only once that call's transaction has actually committed, and push to Redis only
 * then — the same "never send a notification for an event that has not been persisted" principle
 * library-docs.md states for the afterCommit pattern, just needing its own enforcement one layer
 * down, inside this class's own two transactional entry points.
 */
@Service
@RequiredArgsConstructor
class NotificationRowWriter {

    private final NotificationRepository notificationRepository;

    @Transactional
    Notification save(Notification notification) {
        return notificationRepository.save(notification);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Notification saveInNewTransaction(Notification notification) {
        return notificationRepository.save(notification);
    }
}
