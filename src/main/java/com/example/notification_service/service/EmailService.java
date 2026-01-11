package com.example.notification_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final Random random = new Random();
    
    public boolean sendEmail(String to, String subject, String message) {
        logger.info("Attempting to send email to: {} with subject: {}", to, subject);
        
        try {
            // Simulate network timeout (30% chance)
            if (random.nextInt(100) < 30) {
                logger.warn("Simulated network timeout while sending email to: {}", to);
                Thread.sleep(5000); // Simulate timeout
                throw new RuntimeException("Network timeout");
            }
            
            // Simulate email sending failure (20% chance)
            if (random.nextInt(100) < 20) {
                logger.error("Simulated email sending failure to: {}", to);
                throw new RuntimeException("Email service unavailable");
            }
            
            // Simulate successful email sending
            logger.info("Email sent successfully to: {}", to);
            return true;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Email sending interrupted for: {}", to, e);
            return false;
        } catch (Exception e) {
            logger.error("Failed to send email to: {}", to, e);
            return false;
        }
    }
}
