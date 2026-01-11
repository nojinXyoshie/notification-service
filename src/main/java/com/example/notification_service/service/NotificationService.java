package com.example.notification_service.service;

import com.example.notification_service.dto.NotificationRequest;
import com.example.notification_service.dto.NotificationResponse;
import com.example.notification_service.entity.Notification;
import com.example.notification_service.entity.Notification.NotificationStatus;
import com.example.notification_service.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @Autowired
    private EmailService emailService;
    
    public NotificationResponse createNotification(NotificationRequest request) {
        logger.info("Creating notification for transaction: {}", request.getTransactionId());
        
        // Check for existing notification (idempotency check)
        Optional<Notification> existingNotification = notificationRepository
                .findByTransactionIdAndNotificationType(request.getTransactionId(), request.getNotificationType());
        
        if (existingNotification.isPresent()) {
            logger.info("Notification already exists for transaction: {} and type: {}", 
                    request.getTransactionId(), request.getNotificationType());
            return convertToResponse(existingNotification.get());
        }
        
        try {
            Notification notification = convertToEntity(request);
            notification = notificationRepository.save(notification);
            
            // Process notification asynchronously
            processNotificationAsync(notification);
            
            logger.info("Notification created successfully with ID: {}", notification.getId());
            return convertToResponse(notification);
            
        } catch (DataIntegrityViolationException e) {
            logger.error("Duplicate notification attempt for transaction: {}", request.getTransactionId(), e);
            // Handle race condition - another thread created the same notification
            Optional<Notification> duplicateNotification = notificationRepository
                    .findByTransactionIdAndNotificationType(request.getTransactionId(), request.getNotificationType());
            if (duplicateNotification.isPresent()) {
                return convertToResponse(duplicateNotification.get());
            }
            throw e;
        }
    }
    
    @Async("notificationTaskExecutor")
    public void processNotificationAsync(Notification notification) {
        logger.info("Processing notification ID: {}", notification.getId());
        
        try {
            boolean sent = emailService.sendEmail(
                    notification.getRecipient(),
                    notification.getSubject(),
                    notification.getMessage()
            );
            
            if (sent) {
                notification.setStatus(NotificationStatus.SENT);
                notification.setSentAt(LocalDateTime.now());
                logger.info("Notification sent successfully for ID: {}", notification.getId());
            } else {
                throw new RuntimeException("Email service returned false");
            }
            
        } catch (Exception e) {
            logger.error("Failed to send notification ID: {}", notification.getId(), e);
            handleNotificationFailure(notification, e.getMessage());
        }
        
        notificationRepository.save(notification);
    }
    
    private void handleNotificationFailure(Notification notification, String errorMessage) {
        notification.setRetryCount(notification.getRetryCount() + 1);
        notification.setErrorMessage(errorMessage);
        
        if (notification.getRetryCount() >= notification.getMaxRetry()) {
            notification.setStatus(NotificationStatus.FAILED);
            logger.error("Notification failed permanently after {} retries. ID: {}", 
                    notification.getMaxRetry(), notification.getId());
        } else {
            notification.setStatus(NotificationStatus.RETRYING);
            logger.info("Notification will be retried. Attempt {}/{}. ID: {}", 
                    notification.getRetryCount(), notification.getMaxRetry(), notification.getId());
        }
    }
    
    @Scheduled(fixedDelay = 30000) // Run every 30 seconds
    public void retryFailedNotifications() {
        logger.debug("Checking for failed notifications to retry");
        
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5); // Retry notifications older than 5 minutes
        List<Notification> failedNotifications = notificationRepository
                .findFailedNotificationsForRetry(NotificationStatus.FAILED, threshold);
        
        logger.info("Found {} failed notifications to retry", failedNotifications.size());
        
        for (Notification notification : failedNotifications) {
            notification.setStatus(NotificationStatus.RETRYING);
            notificationRepository.save(notification);
            processNotificationAsync(notification);
        }
    }
    
    @Transactional(readOnly = true)
    public Optional<NotificationResponse> getNotification(Long id) {
        return notificationRepository.findById(id)
                .map(this::convertToResponse);
    }
    
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByStatus(NotificationStatus status) {
        return notificationRepository.findByStatus(status)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByTransactionId(String transactionId) {
        return notificationRepository.findAll()
                .stream()
                .filter(n -> n.getTransactionId().equals(transactionId))
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    private Notification convertToEntity(NotificationRequest request) {
        Notification notification = new Notification();
        notification.setTransactionId(request.getTransactionId());
        notification.setNotificationType(request.getNotificationType());
        notification.setRecipient(request.getRecipient());
        notification.setSubject(request.getSubject());
        notification.setMessage(request.getMessage());
        notification.setMaxRetry(request.getMaxRetry());
        return notification;
    }
    
    private NotificationResponse convertToResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setTransactionId(notification.getTransactionId());
        response.setNotificationType(notification.getNotificationType());
        response.setRecipient(notification.getRecipient());
        response.setSubject(notification.getSubject());
        response.setMessage(notification.getMessage());
        response.setStatus(notification.getStatus());
        response.setRetryCount(notification.getRetryCount());
        response.setMaxRetry(notification.getMaxRetry());
        response.setCreatedAt(notification.getCreatedAt());
        response.setUpdatedAt(notification.getUpdatedAt());
        response.setSentAt(notification.getSentAt());
        response.setErrorMessage(notification.getErrorMessage());
        return response;
    }
}
