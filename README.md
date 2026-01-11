# Notification Service

Microservice untuk mengelola notifikasi dalam sistem pembayaran dengan fitur idempotency dan retry mechanism.

## 🏗️ Architecture Overview

Notification Service adalah bagian dari sistem pembayaran yang bertanggung jawab untuk:
- **Menerima callback** dari Payment Service
- **Mengirim notifikasi** ke customer via email
- **Menjamin idempotency** untuk mencegah duplicate notification
- **Menangani network timeout** dengan retry mechanism


## 💳 Payment Flow Explanation

1. **Payment Gateway** mengirim callback ke Payment Service
2. **Payment Service** mengirim notifikasi ke Notification Service
3. **Notification Service** memproses dan mengirim email ke customer
4. **Retry mechanism** menangani failed notifications
5. **Idempotency** mencegah duplicate notifications

## 🔒 Idempotency Implementation

### Database Level
```sql
UNIQUE KEY unique_notification (transaction_id, notification_type)
```

### Application Level
```java
// Check existing notification sebelum create
Optional<Notification> existing = notificationRepository
    .findByTransactionIdAndNotificationType(transactionId, type);

if (existing.isPresent()) {
    return convertToResponse(existing.get()); // Return existing notification
}
```

### Race Condition Handling
```java
try {
    notification = notificationRepository.save(notification);
} catch (DataIntegrityViolationException e) {
    // Handle race condition - cari notification yang sudah ada
    Optional<Notification> duplicate = notificationRepository
        .findByTransactionIdAndNotificationType(transactionId, type);
    return convertToResponse(duplicate.get());
}
```

## 🔄 How Duplicate Callback is Handled

Payment Gateway bisa mengirim callback berkali-kali, Notification Service menanganinya dengan:

1. **Unique Constraint**: Database mencegah duplicate entry
2. **Idempotent Check**: Aplikasi check existing notification
3. **Same Response**: Selalu return notification yang sama untuk request yang sama

**Scenario:**
```
Request 1: TXN123, PAYMENT_SUCCESS → Create new notification ✓
Request 2: TXN123, PAYMENT_SUCCESS → Return existing notification ✓
Request 3: TXN123, PAYMENT_FAILED → Create new notification (different type) ✓
```

## 🛡️ How Double Charge is Prevented

Double charge dicegah melalui beberapa layer:

### 1. Payment Service Level
- Menggunakan idempotency key untuk setiap payment request
- Check status pembayaran sebelum memproses

### 2. Notification Service Level
- Tidak mengirim notifikasi duplikat untuk payment yang sama
- Unique constraint mencegah duplicate notification

### 3. Database Level
- Transaction isolation mencegah race condition
- Unique constraint sebagai safety net

## 🚀 How to Run Each Service

### Notification Service (Ini)
```bash
# Navigate ke project directory
cd notification-service

# Run dengan Maven
./mvnw spring-boot:run

# Atau dengan Java
java -jar target/notification-service-0.0.1-SNAPSHOT.jar
```

**Access:**
- API: http://localhost:8084
- H2 Console: http://localhost:8084/h2-console
  - JDBC URL: `jdbc:h2:mem:testdb`
  - Username: `sa`
  - Password: `password`

### Order Service (Asumsi)
```bash
cd ../order-service
./mvnw spring-boot:run
# Port: 8081
```

### Payment Service (Asumsi)
```bash
cd ../payment-service
./mvnw spring-boot:run
# Port: 8082
```

## 🧪 Example Test Scenario

### Scenario 1: Normal Payment Flow
```bash
# 1. Create notification untuk payment success
curl -X POST http://localhost:8084/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN001",
    "notificationType": "PAYMENT_SUCCESS",
    "recipient": "customer@example.com",
    "subject": "Payment Successful",
    "message": "Your payment has been processed successfully."
  }'

# Response: 201 Created dengan notification ID
```

### Scenario 2: Duplicate Callback Test
```bash
# Request pertama - create new notification
curl -X POST http://localhost:8084/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN001",
    "notificationType": "PAYMENT_SUCCESS",
    "recipient": "customer@example.com",
    "subject": "Payment Successful",
    "message": "Your payment has been processed successfully."
  }'

# Response: 201 Created (ID: 1)

# Request kedua (duplicate) - return existing
curl -X POST http://localhost:8084/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN001",
    "notificationType": "PAYMENT_SUCCESS",
    "recipient": "customer@example.com",
    "subject": "Payment Successful",
    "message": "Your payment has been processed successfully."
  }'

# Response: 200 OK dengan notification ID yang sama (ID: 1)
```

### Scenario 3: Payment Callback Integration
```bash
# Simulasi payment callback dari payment gateway
curl -X POST http://localhost:8084/api/notifications/payment-callback \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN002",
    "status": "SUCCESS",
    "customerEmail": "customer@example.com"
  }'

# Response: 200 OK - "Callback processed successfully"
```

### Scenario 4: Network Timeout & Retry Test
```bash
# Create notification (mungkin gagal karena simulated timeout)
curl -X POST http://localhost:8084/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN003",
    "notificationType": "PAYMENT_SUCCESS",
    "recipient": "customer@example.com",
    "subject": "Payment Successful",
    "message": "Your payment has been processed successfully."
  }'

# Cek status notification
curl http://localhost:8084/api/notifications/1

# Tunggu 30 detik untuk retry otomatis
# Cek lagi statusnya - seharusnya berubah dari FAILED ke RETRYING ke SENT
```

### Scenario 5: Monitoring Notifications
```bash
# Lihat semua pending notifications
curl "http://localhost:8084/api/notifications?status=PENDING"

# Lihat notifications untuk transaction tertentu
curl "http://localhost:8084/api/notifications/transaction/TXN001"
```

## 📊 Monitoring & Logging

### Log Levels
- `com.example.notification_service`: INFO
- `org.springframework.scheduling`: DEBUG

### Important Logs
- Notification creation: `"Creating notification for transaction: {}"`
- Idempotency check: `"Notification already exists for transaction: {}"`
- Retry attempts: `"Notification will be retried. Attempt {}/{}. ID: {}"`
- Network timeout: `"Simulated network timeout while sending email to: {}"`

## 🔧 Configuration

### Application Properties
```properties
# Server Configuration
server.port=8084
spring.application.name=notification-service

# Database Configuration
spring.datasource.url=jdbc:h2:mem:testdb
spring.h2.console.enabled=true

# Async Configuration
spring.task.execution.pool.core-size=5
spring.task.execution.pool.max-size=10

# Retry Configuration
- Retry setiap 30 detik
- Max retry: 3 kali
- Retry threshold: 5 menit
```

## 🛠️ API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/notifications` | Create notification |
| GET | `/api/notifications/{id}` | Get notification by ID |
| GET | `/api/notifications?status={status}` | Get by status |
| GET | `/api/notifications/transaction/{transactionId}` | Get by transaction |
| POST | `/api/notifications/payment-callback` | Payment callback handler |

## 📝 Notification Status

- **PENDING**: Notification dibuat, menunggu proses
- **SENT**: Notification berhasil dikirim
- **FAILED**: Notification gagal setelah max retry
- **RETRYING**: Notification gagal tapi akan di-retry

## 🎯 Key Features untuk Assessment

✅ **Idempotency**: Mencegah duplicate notification  
✅ **Retry Mechanism**: Otomatis retry failed notification  
✅ **Network Timeout Handling**: Simulasi dan handling timeout  
✅ **Async Processing**: Non-blocking notification sending  
✅ **Comprehensive Logging**: Monitoring dan debugging  
✅ **RESTful API**: Standard REST endpoints  
✅ **Database Integration**: H2 dengan JPA  
✅ **Error Handling**: Global exception handling  


