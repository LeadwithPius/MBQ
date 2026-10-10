MBQ

A digital patient triage queue and blood stock coordination system for a hospital facility. Built as a React single-page app on top of a Spring Boot REST API with a PostgreSQL database.

Overview

MBQ digitises reception and triage queue management and gives lab/blood bank staff live visibility of blood stock. A case flagged as an emergency at triage can trigger a blood stock check and a (simulated) donor alert within the same system.

Modules

Module	What it does
Reception	Registers patients and generates timestamped queue tickets assigned to a department.
Queue dashboard	Shows live ticket order, status and position; supports "call next" and priority flagging.
Blood bank	CRUD for blood stock per blood group, configurable low-stock threshold, simulated donor alert log.
Reporting	Daily patient volume and blood stock movement summaries.
Auth	Stateless JWT login with role-based access control.

Roles: RECEPTIONIST, TRIAGE_NURSE, DOCTOR, LAB_STAFF, ADMIN

Tech Stack
Layer	Technology
Frontend	React.js (JavaScript / JSX)
Backend	Java, Spring Boot (REST controllers, service layer)
Persistence	Spring Data JPA / Hibernate
Database	PostgreSQL
Security	Spring Security, stateless JWT, RBAC, BCrypt password hashing
Testing	JUnit, Spring Boot Test
Build	Maven (backend), npm (frontend)
Architecture
┌────────────────────────────────────────────┐
│ Presentation Layer                         │
│ React SPA: Reception UI, Triage Dashboard, │
│ Lab Console, Admin Panel                   │
└────────────────────┬───────────────────────┘
                     │  REST / JSON over HTTPS
┌────────────────────▼───────────────────────┐
│ API & Security Layer                       │
│ Spring Boot controllers                    │
│ Spring Security: JWT validation + RBAC     │
└────────────────────┬───────────────────────┘
                     │
┌────────────────────▼───────────────────────┐
│ Business Logic Layer                       │
│ Queue Manager · Triage Priority ·          │
│ Blood Stock · Alert Service · Reports      │
└────────────────────┬───────────────────────┘
                     │
┌────────────────────▼───────────────────────┐
│ Data Access Layer                          │
│ Spring Data JPA repositories / Hibernate   │
└────────────────────┬───────────────────────┘
                     │  JDBC
┌────────────────────▼───────────────────────┐
│ PostgreSQL                                 │
└────────────────────────────────────────────┘

Design notes:

Layered, object-oriented structure so modules (reception, queue, blood bank, reporting) can change independently.
Database writes are transactional so queue and stock data stay consistent after an unexpected shutdown.
Relevant tables carry a facility_id so the schema can extend to multiple facilities later.
Data Model
Entity	Key fields
Facility	FacilityID (PK), Name, Location
Department	DeptID (PK), Name, FacilityID (FK)
StaffUser	StaffID (PK), Name, Role, DeptID (FK), Username
Patient	PatientID (PK), Name, Age_Sex, Contact, BloodGroup
QueueTicket	TicketID (PK), PatientID (FK), DeptID (FK), PriorityLevel, Status, TimeIssued
BloodStock	StockID (PK), FacilityID (FK), BloodGroup, UnitsAvailable, LastUpdated
Donor	DonorID (PK), Name, BloodGroup, Contact, Eligible
Alert	AlertID (PK), StockID (FK), DonorID (FK), DateSent, ResponseStatus

Relationships: a facility houses blood stock and contains departments; a department has staff and receives queue tickets; a patient has queue tickets; blood stock generates alerts, and each alert is addressed to a donor.

Security
Passwords are hashed with BCrypt.
Login returns a signed JWT; the frontend sends it as Authorization: Bearer <token>.
The API is stateless (no server-side sessions).
Every endpoint is restricted by role, so a user can only call actions relevant to their role.
Accounts are created by an admin. There is no self-registration and no third-party OAuth.
REST API (indicative)

Endpoint names below are a planned outline and may change during development.

Area	Method & path	Roles
Auth	POST /api/auth/login	Public
Users	GET/POST/PUT/DELETE /api/users	Admin
Patients	POST /api/patients, GET /api/patients/{id}	Receptionist
Queue	GET /api/queue	All staff
Queue	POST /api/queue/next	Triage Nurse
Queue	PUT /api/queue/{ticketId}/priority	Triage Nurse
Consultation	PUT /api/queue/{ticketId}/outcome	Doctor
Blood stock	GET /api/blood-stock	Lab Staff, Doctor
Blood stock	POST/PUT/DELETE /api/blood-stock/{id}	Lab Staff
Alerts	GET /api/alerts, POST /api/alerts/donor	Lab Staff
Reports	GET /api/reports/daily	Admin


Prerequisites
Java JDK 17+
Maven 3.8+
Node.js 18+ and npm
PostgreSQL 14+