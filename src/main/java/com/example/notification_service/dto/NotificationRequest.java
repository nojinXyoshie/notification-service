package com.example.notification_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class NotificationRequest {
    
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    @NotBlank(message = "Notification type is required")
    private String notificationType;
    
    @NotBlank(message = "Recipient is required")
    @Email(message = "Invalid email format")
    private String recipient;
    
    @Size(max = 200, message = "Subject must not exceed 200 characters")
    private String subject;
    
    @NotEmpty(message = "Message is required")
    private String message;
    
    private Integer maxRetry = 3;
    
    public NotificationRequest() {}
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
    
    public String getNotificationType() {
        return notificationType;
    }
    
    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }
    
    public String getRecipient() {
        return recipient;
    }
    
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Integer getMaxRetry() {
        return maxRetry;
    }
    
    public void setMaxRetry(Integer maxRetry) {
        this.maxRetry = maxRetry;
    }
}
