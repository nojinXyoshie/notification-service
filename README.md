# Notification Service - Microservice Notifikasi Pembayaran

## 🏗️ Gambaran Arsitektur

Ini adalah microservice Spring Boot yang menangani pengiriman notifikasi pembayaran dengan jaminan idempotensi dan mekanisme retry. Sistem dirancang untuk menangani callback dari payment service, mencegah double notifikasi, dan menangani network timeout dengan efektif.

### Komponen Utama
- **Notification Service**: Menerima callback dan mengirim notifikasi ke customer
- **Email Service**: Mengirim email dengan simulasi network timeout
- **H2 Database**: Database in-memory untuk notifikasi
- **Async Processor**: Pemrosesan notifikasi non-blocking

## 💳 Alur Notifikasi

1. **Payment Gateway** mengirim callback ke Payment Service
2. **Payment Service** mengirim notifikasi ke Notification Service
3. **Notification Service** memproses dan mengirim email ke customer
4. **Retry mechanism** menangani failed notifications
5. **Idempotency** mencegah duplicate notifications

## 🔒 Implementasi Idempotensi

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

## 🔄 Penanganan Callback Ganda

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

## 🛡️ Pencegahan Double Notifikasi

Double notifikasi dicegah melalui beberapa layer:

### 1. Notification Service Level
- Menggunakan unique constraint untuk setiap notification request
- Check existing notification sebelum memproses

### 2. Database Level
- Transaction isolation mencegah race condition
- Unique constraint sebagai safety net

## 🌐 Penanganan Network Timeout

1. **Mekanisme Retry**: Menggunakan scheduled retry setiap 30 detik
2. **Simulasi Timeout**: Email service mensimulasikan network timeout (30% chance)
3. **Async Processing**: Non-blocking notification processing
4. **Graceful Degradation**: Sistem terus berfungsi meskipun ada kegagalan

## 🚀 Cara Menjalankan Service

### Prasyarat
- Java 17+
- Maven 3.6+

### Langkah-langkah
1. **Clone dan build**
   ```bash
   git clone https://github.com/nojinXyoshie/notification-service.git
   cd notification-service
   mvn clean install
   ```

2. **Jalankan aplikasi**
   ```bash
   mvn spring-boot:run
   ```
   
   Aplikasi akan berjalan di `http://localhost:8084` 

3. **Akses H2 Console** (opsional)
   - URL: `http://localhost:8084/h2-console` 
   - JDBC URL: `jdbc:h2:mem:testdb` 
   - Username: `sa` 
   - Password: (kosong)

## 🧪 Contoh Skenario Test

### 1. Buat Notifikasi
```bash
curl -X POST http://localhost:8084/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN001",
    "notificationType": "PAYMENT_SUCCESS",
    "recipient": "customer@example.com",
    "subject": "Payment Successful",
    "message": "Your payment has been processed successfully."
  }'
```

**Respons:**
```json
{
  "id": 1,
  "transactionId": "TXN001",
  "notificationType": "PAYMENT_SUCCESS",
  "recipient": "customer@example.com",
  "subject": "Payment Successful",
  "message": "Your payment has been processed successfully.",
  "status": "PENDING",
  "retryCount": 0,
  "maxRetry": 3,
  "createdAt": "2026-01-11T12:00:00",
  "updatedAt": "2026-01-11T12:00:00"
}
```

### 2. Simulasikan Callback Sukses Pembayaran
```bash
curl -X POST http://localhost:8084/api/notifications/payment-callback \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN002",
    "status": "SUCCESS",
    "customerEmail": "customer@example.com"
  }'
```

### 3. Cek Status Notifikasi
```bash
curl http://localhost:8084/api/notifications/1
```

**Respons yang Diharapkan:**
```json
{
  "id": 1,
  "transactionId": "TXN001",
  "notificationType": "PAYMENT_SUCCESS",
  "status": "SENT",
  "sentAt": "2026-01-11T12:01:00"
}
```

### 4. Test Idempotensi (Callback Duplikat)
Kirim callback yang sama lagi - status harus tetap sama dan sistem mencatat idempotent ignore.

### 5. Test Retry Mechanism
Buat beberapa notifikasi - beberapa akan gagal karena simulated timeout, tunggu 30 detik untuk retry otomatis.

## 🛠️ API Endpoints

### Pemrosesan Notifikasi
- `POST /api/notifications` - Buat notifikasi baru
- `GET /api/notifications/{id}` - Dapatkan detail notifikasi
- `GET /api/notifications?status={status}` - Dapatkan notifikasi berdasarkan status
- `GET /api/notifications/transaction/{transactionId}` - Dapatkan notifikasi per transaksi

### Callback Pembayaran
- `POST /api/notifications/payment-callback` - Terima status pembayaran dari payment service

### Database Console
- `GET /h2-console` - Akses H2 database console (http://localhost:8084/h2-console)

## 📊 Monitoring & Logging

### Log Levels
- `com.example.notification_service`: INFO
- `org.springframework.scheduling`: DEBUG

### Important Logs
- Notification creation: `"Creating notification for transaction: {}"`
- Idempotency check: `"Notification already exists for transaction: {}"`
- Retry attempts: `"Notification will be retried. Attempt {}/{}. ID: {}"`
- Network timeout: `"Simulated network timeout while sending email to: {}"`

## 🔧 Konfigurasi

Service dapat dikonfigurasi melalui `application.properties`:

```properties
# Server Configuration
server.port=8084
spring.application.name=notification-service

# Database Configuration
spring.datasource.url=jdbc:h2:mem:testdb
spring.h2.console.enabled=true
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true

# Async Configuration
spring.task.execution.pool.core-size=5
spring.task.execution.pool.max-size=10
spring.task.execution.pool.queue-capacity=100

# Logging Configuration
logging.level.com.example.notification_service=INFO
logging.level.org.springframework.scheduling=DEBUG
```

## 🎯 Keputusan Desain Utama

1. **Async Processing**: Menggunakan `@Async` untuk non-blocking notification sending
2. **Database First**: Menggunakan database sebagai source of truth untuk idempotency
3. **Scheduled Retry**: Menggunakan `@Scheduled` untuk reliable retry mechanism
4. **Pemisahan Tanggung Jawab**: Dedicated EmailService untuk simulasi dan future real integration

## 📋 Cakupan Persyaratan Assessment

✅ **Penanganan Callback Payment Gateway**: Menangani multiple callback dari payment gateway
✅ **Penanganan Network Timeout**: Implementasi mekanisme retry dengan exponential backoff
✅ **Pencegahan Double Charge**: Idempotensi memastikan tidak ada notifikasi duplikat
✅ **Arsitektur Microservice**: Pemisahan tanggung jawab yang jelas
✅ **Error Handling yang Kuat**: Penanganan error dan logging yang komprehensif

## 🛠️ Teknologi Stack

- **Framework**: Spring Boot 3.5.9
- **Database**: H2 (in-memory)
- **Build Tool**: Maven
- **Java Version**: 17
- **Validation**: Jakarta Bean Validation
- **Async Processing**: Spring Async dengan thread pool
- **HTTP Client**: Simulasi email service dengan network timeout

## 📝 Status Notifikasi

- **PENDING**: Notifikasi dibuat, menunggu proses
- **SENT**: Notifikasi berhasil dikirim
- **FAILED**: Notifikasi gagal setelah max retry
- **RETRYING**: Notifikasi gagal tapi akan di-retry

