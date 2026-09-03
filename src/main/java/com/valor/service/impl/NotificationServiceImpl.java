package com.valor.service.impl;

import com.valor.entity.Notification;
import com.valor.enums.NotificationStatus;
import com.valor.exception.ResourceNotFoundException;
import com.valor.repository.NotificationRepository;
import com.valor.request.NotificationRequest;
import com.valor.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public Notification createNotification(NotificationRequest request) {
        Notification notification = new Notification();
        notification.setRecipientType(request.recipientType());
        notification.setRecipientId(request.recipientId());
        notification.setTitle(request.title());
        notification.setMessage(request.message());
        notification.setChannel(request.channel());
        notification.setStatus(request.status() == null ? NotificationStatus.PENDING : request.status());
        notification.setScheduledAt(request.scheduledAt());
        return notificationRepository.save(notification);
    }

    @Override
    public Notification getNotification(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
    }

    @Override
    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    @Override
    public Page<Notification> searchNotifications(String term, Pageable pageable) {
        return notificationRepository.search(term, pageable);
    }

    @Override
    public Page<Notification> getByRecipientType(String recipientType, Pageable pageable) {
        return notificationRepository.findByRecipientTypeIgnoreCase(recipientType, pageable);
    }

    @Override
    public Page<Notification> getByStatus(NotificationStatus status, Pageable pageable) {
        return notificationRepository.findByStatus(status, pageable);
    }

    @Override
    public Notification markSent(Long id) {
        Notification notification = getNotification(id);
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
        return notificationRepository.save(notification);
    }

    @Override
    public Notification markRead(Long id) {
        Notification notification = getNotification(id);
        notification.setStatus(NotificationStatus.READ);
        notification.setReadAt(LocalDateTime.now());
        return notificationRepository.save(notification);
    }
}