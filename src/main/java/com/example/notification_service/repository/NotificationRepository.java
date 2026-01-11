package com.example.notification_service.repository;

import com.example.notification_service.entity.Notification;
import com.example.notification_service.entity.Notification.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    Optional<Notification> findByTransactionIdAndNotificationType(String transactionId, String notificationType);
    
    List<Notification> findByStatus(NotificationStatus status);
    
    List<Notification> findByStatusAndRetryCountLessThan(NotificationStatus status, Integer retryCount);
    
    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.retryCount < n.maxRetry AND n.createdAt < :threshold")
    List<Notification> findFailedNotificationsForRetry(@Param("status") NotificationStatus status, 
                                                     @Param("threshold") LocalDateTime threshold);
    
    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.createdAt BETWEEN :start AND :end")
    List<Notification> findByStatusAndCreatedAtBetween(@Param("status") NotificationStatus status,
                                                      @Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);
    
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.status = :status AND n.createdAt BETWEEN :start AND :end")
    Long countByStatusAndCreatedAtBetween(@Param("status") NotificationStatus status,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);
}
