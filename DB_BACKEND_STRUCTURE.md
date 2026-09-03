# Valor Lift Services & Maintenance Backend Design

## 1. Proposed Folder Structure

Backend/src/main/java/com/valor
- controller
- dto
- entity
- enums
- exception
- mapper
- repository
- request
- response
- security
- service
- service/impl
- audit

Backend/src/main/resources
- application.properties
- db/migration

## 2. Entity/table mapping

### customers
- Entity: `Customer`
- Fields: id, name, email, passwordHash, phone, alternatePhone, address, city, state, pincode, status, createdAt, updatedAt
- Relationships: one-to-many with `Building`, `ServiceRequest`, `Payment`, `Notification`

### buildings
- Entity: `Building`
- Fields: id, customer, buildingName, buildingType, address, city, state, pincode, numberOfLifts, emergencyContactName, emergencyContactPhone, status, createdAt, updatedAt
- Relationships: many-to-one with `Customer`; one-to-many with `Lift`

### lifts
- Entity: `Lift`
- Fields: id, building, liftNumber, name, model, manufacturer, capacity, floorCount, serialNumber, installationDate, location, currentStatus, warrantyStatus, warrantyStartDate, warrantyEndDate, lastMaintenanceDate, nextMaintenanceDate, totalBreakdowns, healthScore, machineRoom, qrCode, specifications, status, createdAt, updatedAt
- Relationships: many-to-one with `Building`; one-to-many with `Amc`, `ServiceRequest`

### amcs
- Entity: `Amc`
- Fields: id, lift, amcNumber, plan, coverageDetails, startDate, endDate, freeMonthEndDate, renewalDate, amount, paidAmount, pendingAmount, paymentStatus, status, renewalCount, createdAt, updatedAt
- Relationships: many-to-one with `Lift`; one-to-many with `Payment`

### technicians
- Entity: `Technician`
- Fields: id, employeeId, name, email, passwordHash, phone, assignedArea, specialization, currentWorkload, pendingJobs, rating, role, availabilityStatus, status, createdAt, updatedAt
- Relationships: one-to-many with `ServiceRequest`, `ServiceHistory`, `ServicePhoto`, `InventoryTransaction`

### admins
- Entity: `Admin`
- Fields: id, employeeId, name, email, passwordHash, phone, designation, role, active, createdAt, updatedAt

### service_requests
- Entity: `ServiceRequest`
- Fields: id, customer, lift, assignedTechnician, serviceNumber, title, description, issueCategory, priority, status, serviceType, customerRemarks, technicianRemarks, serviceRequestedAt, preferredVisitDate, preferredTimeSlot, startedAt, completedAt, cancellationReason, internalAdminNotes, createdAt, updatedAt
- Relationships: many-to-one with `Customer`, `Lift`, `Technician`; one-to-many with `ServiceHistory`, `ServicePhoto`; one-to-one/optional with `ServiceFeedback`

### service_history
- Entity: `ServiceHistory`
- Fields: id, serviceRequest, technician, action, status, remarks, startedAt, completedAt, createdAt
- Relationships: many-to-one with `ServiceRequest`, `Technician`

### service_photos
- Entity: `ServicePhoto`
- Fields: id, serviceRequest, technician, photoUrl, photoType, description, uploadedAt
- Relationships: many-to-one with `ServiceRequest`, `Technician`

### service_feedback
- Entity: `ServiceFeedback`
- Fields: id, serviceRequest, customer, rating, comment, createdAt, updatedAt
- Relationships: many-to-one with `ServiceRequest`, `Customer`

### inventory
- Entity: `Inventory`
- Fields: id, itemName, sku, description, stockQuantity, reorderLevel, unit, location, status, createdAt, updatedAt

### inventory_transactions
- Entity: `InventoryTransaction`
- Fields: id, inventory, technician, serviceRequest, transactionType, quantity, referenceNumber, remarks, createdAt
- Relationships: many-to-one with `Inventory`, `Technician`, optional `ServiceRequest`

### payments
- Entity: `Payment`
- Fields: id, customer, amc, serviceRequest, amount, paymentMethod, paymentStatus, transactionId, paymentDate, createdAt, updatedAt
- Relationships: many-to-one with `Customer`, optional `Amc`, optional `ServiceRequest`

### notifications
- Entity: `Notification`
- Fields: id, recipientType, recipientId, title, message, channel, status, scheduledAt, sentAt, readAt, createdAt

## 3. Suggested package contents

### controller
- AuthController
- CustomerController
- BuildingController
- LiftController
- AmcController
- ServiceRequestController
- TechnicianController
- InventoryController
- PaymentController
- NotificationController
- ReportController

### service
- AuthService
- CustomerService
- BuildingService
- LiftService
- AmcService
- ServiceRequestService
- TechnicianService
- InventoryService
- PaymentService
- NotificationService
- ReportService

### repository
- CustomerRepository
- BuildingRepository
- LiftRepository
- AmcRepository
- ServiceRequestRepository
- ServiceHistoryRepository
- ServicePhotoRepository
- ServiceFeedbackRepository
- TechnicianRepository
- AdminRepository
- InventoryRepository
- InventoryTransactionRepository
- PaymentRepository
- NotificationRepository

### dto/request
- RegisterRequest
- LoginRequest
- CustomerUpdateRequest
- BuildingRequest
- LiftRequest
- AmcRequest
- ServiceRequestCreateRequest
- ServiceRequestUpdateRequest
- ServiceAssignRequest
- ServiceStatusUpdateRequest
- TechnicianAvailabilityRequest
- InventoryRequest
- InventoryTransactionRequest
- PaymentRequest
- NotificationRequest

### dto/response
- AuthResponse
- CustomerResponse
- BuildingResponse
- LiftResponse
- AmcResponse
- ServiceRequestResponse
- TechnicianResponse
- InventoryResponse
- PaymentResponse
- NotificationResponse
- ReportSummaryResponse

## 4. Security and authentication

### security
- `JwtTokenProvider` for token generation and validation
- `JwtAuthenticationFilter` to inspect bearer tokens
- `SecurityConfig` to configure endpoint authorization
- `CustomUserDetailsService` for loading customer/technician/admin credentials
- Password hashing with `BCryptPasswordEncoder`
- Role-based access control using `ROLE_CUSTOMER`, `ROLE_TECHNICIAN`, `ROLE_ADMIN`

## 5. Exception handling

### exception package
- `ApiException`
- `ResourceNotFoundException`
- `DuplicateResourceException`
- `UnauthorizedException`
- `BadRequestException`
- `GlobalExceptionHandler`

## 6. Example application.properties

spring.datasource.url=jdbc:mysql://localhost:3306/valor_lift_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect

jwt.secret=valor-lift-secret-key-change-in-production
jwt.expiration=86400000
jwt.refresh-expiration=604800000

## 7. Sample API flow

1. Register customer: `POST /api/auth/register`
2. Login customer: `POST /api/auth/login`
3. Create building: `POST /api/buildings`
4. Create lift: `POST /api/lifts`
5. Create AMC: `POST /api/amcs`
6. Create service request: `POST /api/service-requests`
7. Assign technician: `PUT /api/service-requests/{id}/assign`
8. Update inventory transaction: `POST /api/inventory/{id}/transactions`
9. Record payment: `POST /api/payments`
10. Send notification: `POST /api/notifications`

## 8. Design notes

- `customers` is the root business entity. It does not duplicate building or lift data.
- `buildings` point to `customers`; `lifts` point to `buildings`.
- `service_requests` point to `customers`, `lifts`, and `technicians` only by ID.
- `amcs` point to `lifts`, and `payments` can link to `amcs` or `service_requests`.
- `inventory_transactions` record every stock movement; inventory quantity is derived, not overwritten directly.
- `notifications` are stored with `recipientType` and `recipientId` instead of multi-table duplication.

## 9. SQL sample bodies and testing order

### Customer registration body
```
{
  "name": "Rama Krishna",
  "email": "rama@example.com",
  "password": "SecurePass123!",
  "phone": "9999999999",
  "address": "123 Main Street",
  "city": "Hyderabad",
  "state": "Telangana",
  "pincode": "500001"
}
```

### Service request creation body
```
{
  "customerId": 1,
  "liftId": 1,
  "assignedTechnicianId": 1,
  "serviceNumber": "SR-0001",
  "title": "Lift not stopping at floor 5",
  "description": "Passenger elevator overshoots floor 5.",
  "issueCategory": "MECHANICAL",
  "priority": "HIGH",
  "serviceType": "BREAKDOWN",
  "customerRemarks": "Elevator jerks while stopping.",
  "preferredVisitDate": "2026-08-13",
  "preferredTimeSlot": "10:00-12:00"
}
```

### API testing order
1. `POST /api/auth/register`
2. `POST /api/auth/login`
3. `GET /api/auth/me`
4. `POST /api/buildings`
5. `POST /api/lifts`
6. `POST /api/amcs`
7. `POST /api/service-requests`
8. `PUT /api/service-requests/{id}/assign`
9. `PUT /api/service-requests/{id}/start`
10. `PUT /api/service-requests/{id}/complete`
11. `POST /api/inventory/{id}/transactions`
12. `POST /api/payments`
13. `POST /api/notifications`
