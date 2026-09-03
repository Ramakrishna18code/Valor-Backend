package com.valor.service;

import com.valor.entity.Notification;
import com.valor.enums.NotificationStatus;
import com.valor.request.NotificationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationService {
    Notification createNotification(NotificationRequest request);
    Notification getNotification(Long id);
    List<Notification> getAllNotifications();
    Page<Notification> searchNotifications(String term, Pageable pageable);
    Page<Notification> getByRecipientType(String recipientType, Pageable pageable);
    Page<Notification> getByStatus(NotificationStatus status, Pageable pageable);
    Notification markSent(Long id);
    Notification markRead(Long id);
}