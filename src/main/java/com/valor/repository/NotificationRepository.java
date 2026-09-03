package com.valor.repository;

import com.valor.entity.Notification;
import com.valor.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientTypeIgnoreCase(String recipientType, Pageable pageable);

    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    @Query("""
            select n from Notification n
            where lower(coalesce(n.title, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(n.message, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(n.recipientType, '')) like lower(concat('%', :term, '%'))
            """)
    Page<Notification> search(String term, Pageable pageable);
}