package com.valor.notifications;

import com.valor.auth.User;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// Restrict the repository API to retained-record operations; no delete methods.
interface NotificationRepository extends Repository<Notification, Long> {
    Notification save(Notification notification);
    void flush();

    @Query("select n from Notification n where n.recipient.id = :recipientId and n.channel = com.valor.notifications.NotificationChannel.IN_APP and (n.scheduledAt is null or n.scheduledAt <= :asOf) and (:status is null or n.status = :status)")
    Page<Notification> inbox(@Param("recipientId") Long recipientId, @Param("asOf") LocalDateTime asOf,
            @Param("status") NotificationStatus status, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id")
    Optional<Notification> forRead(@Param("id") Long id);
}

interface NotificationRecipientRepository extends Repository<User, Long> {
    Optional<User> findById(Long id);
}
