package com.valor.workflow;

import com.valor.notifications.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class ReassignmentNotificationListener {
    private final NotificationService notifications;

    public ReassignmentNotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(ReassignmentNotificationEvent event) {
        notifications.createSystemOnce(event.incomingUserId(), "reassignment:" + event.requestId() + ":incoming",
                "New job assigned", "Service request #" + event.serviceRequestId() + " was assigned to you after a technician handover. Open Jobs to review it.");
        notifications.createSystemOnce(event.outgoingUserId(), "reassignment:" + event.requestId() + ":outgoing",
                "Job handed over", "Service request #" + event.serviceRequestId() + " has been handed over and is no longer your active assignment.");
    }
}
