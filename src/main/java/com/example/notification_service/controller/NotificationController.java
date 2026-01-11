package com.example.notification_service.controller;

import com.example.notification_service.dto.NotificationRequest;
import com.example.notification_service.dto.NotificationResponse;
import com.example.notification_service.entity.Notification.NotificationStatus;
import com.example.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);
    
    @Autowired
    private NotificationService notificationService;
    
    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(@Valid @RequestBody NotificationRequest request) {
        logger.info("Received notification creation request for transaction: {}", request.getTransactionId());
        
        try {
            NotificationResponse response = notificationService.createNotification(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Failed to create notification for transaction: {}", request.getTransactionId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getNotification(@PathVariable Long id) {
        logger.info("Retrieving notification with ID: {}", id);
        
        Optional<NotificationResponse> response = notificationService.getNotification(id);
        return response.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(@RequestParam(required = false) NotificationStatus status) {
        if (status != null) {
            logger.info("Retrieving notifications with status: {}", status);
            List<NotificationResponse> notifications = notificationService.getNotificationsByStatus(status);
            return ResponseEntity.ok(notifications);
        } else {
            logger.info("Retrieving all notifications");
            // For now, return notifications by status PENDING to avoid returning all notifications
            List<NotificationResponse> notifications = notificationService.getNotificationsByStatus(NotificationStatus.PENDING);
            return ResponseEntity.ok(notifications);
        }
    }
    
    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<List<NotificationResponse>> getNotificationsByTransactionId(@PathVariable String transactionId) {
        logger.info("Retrieving notifications for transaction ID: {}", transactionId);
        
        List<NotificationResponse> notifications = notificationService.getNotificationsByTransactionId(transactionId);
        return ResponseEntity.ok(notifications);
    }
    
    @PostMapping("/payment-callback")
    public ResponseEntity<String> handlePaymentCallback(@RequestBody PaymentCallbackRequest callback) {
        logger.info("Received payment callback for transaction: {}", callback.getTransactionId());
        
        try {
            // Create notification based on payment status
            NotificationRequest notificationRequest = new NotificationRequest();
            notificationRequest.setTransactionId(callback.getTransactionId());
            notificationRequest.setNotificationType("PAYMENT_" + callback.getStatus());
            notificationRequest.setRecipient(callback.getCustomerEmail());
            
            if ("SUCCESS".equals(callback.getStatus())) {
                notificationRequest.setSubject("Payment Successful");
                notificationRequest.setMessage("Your payment for transaction " + callback.getTransactionId() + " has been processed successfully.");
            } else {
                notificationRequest.setSubject("Payment Failed");
                notificationRequest.setMessage("Your payment for transaction " + callback.getTransactionId() + " has failed. Please try again.");
            }
            
            notificationService.createNotification(notificationRequest);
            
            return ResponseEntity.ok("Callback processed successfully");
        } catch (Exception e) {
            logger.error("Failed to process payment callback for transaction: {}", callback.getTransactionId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process callback");
        }
    }
    
    // DTO for payment callback
    public static class PaymentCallbackRequest {
        private String transactionId;
        private String status;
        private String customerEmail;
        
        public String getTransactionId() {
            return transactionId;
        }
        
        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public String getCustomerEmail() {
            return customerEmail;
        }
        
        public void setCustomerEmail(String customerEmail) {
            this.customerEmail = customerEmail;
        }
    }
}
