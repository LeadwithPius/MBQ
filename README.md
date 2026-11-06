MBQ

A digital patient triage queue and blood stock coordination system for a hospital facility. Built as a React single-page app on top of a Spring Boot REST API with a PostgreSQL database.

Overview

MBQ digitises reception and triage queue management and gives lab/blood bank staff live visibility of blood stock. A case flagged as an emergency at triage can trigger a blood stock check and a (simulated) donor alert within the same system.

Modules

Module	What it does
Reception	Registers patients and generates timestamped queue tickets assigned to a department.
Queue dashboard	Shows live ticket order, status and position; nurses can call the next patient and triage staff can flag priority.
Clinical triage	Nurses record observations and vitals; doctors review risk recommendations and read the nurse's notes.
Doctor consultations	Doctors document presenting complaints, history, examination, diagnosis, treatment, prescriptions, investigations and follow-up; finalized notes are read-only.
Laboratory	Doctors request tests from an open consultation; lab staff start, result (immutable) or cancel them; doctors see results in the consultation.
Blood bank	Batch-level stock (receive, verify, FEFO issue, idempotent), ABO/RhD compatibility check when issuing for a patient, low-stock threshold, transparent demand forecast, simulated donor alerts.
Donations	Record a donation from an eligible donor (56-day interval); only donations that pass screening enter stock, as a batch awaiting verification.
Reporting	Daily patient volume and blood stock movement summaries.
Auth	Stateless JWT login with role-based access control.

Roles: RECEPTIONIST, NURSE, DOCTOR, LAB_STAFF, ADMIN

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

The main implemented workflow endpoints are listed below.

Area	Method & path	Roles
Auth	POST /api/auth/login	Public
Patients	GET/POST /api/patients	Admin, Doctor, Nurse, Receptionist (POST: Admin, Receptionist)
Queue	GET /api/queue/departments/{departmentId}/waiting	Admin, Doctor, Nurse, Receptionist
Queue	GET /api/queue/departments/{departmentId}/in-progress	Admin, Doctor, Nurse, Receptionist
Queue	POST /api/queue/departments/{departmentId}/call-next	Admin, Doctor, Nurse
Queue	PATCH /api/queue/tickets/{ticketId}/priority	Admin, Doctor, Nurse
Triage	POST /api/triage/observations	Admin, Nurse
Triage	GET /api/triage/patients/{patientId}/observations	Admin, Doctor, Nurse
Consultation	POST /api/consultations	Admin, Doctor (save/update draft)
Consultation	GET /api/consultations/tickets/{ticketId}	Admin, Doctor
Consultation	GET /api/consultations/patients/{patientId}	Admin, Doctor
Consultation	PATCH /api/consultations/{consultationId}/complete	Admin, Doctor
Auth	POST /api/auth/change-password	Any signed-in staff
Laboratory	POST /api/lab/requests	Admin, Doctor
Laboratory	GET /api/lab/requests[?status=]	Admin, Lab Staff (worklist)
Laboratory	GET /api/lab/requests/consultations/{id}, /patients/{id}	Admin, Doctor, Lab Staff
Laboratory	PATCH /api/lab/requests/{id}/start, /complete	Admin, Lab Staff
Laboratory	PATCH /api/lab/requests/{id}/cancel	Admin, Doctor, Lab Staff
Blood bank	GET /api/blood-bank/stock, /requests, /donors, /alerts	see BloodBankController
Blood bank	POST /api/blood-bank/inventory/batches (Idempotency-Key)	Admin, Lab Staff
Blood bank	POST /api/blood-bank/inventory/issues (Idempotency-Key, optional patientId)	Admin, Lab Staff, Doctor
Blood bank	PATCH /api/blood-bank/inventory/batches/{id}/verify	Admin, Lab Staff
Blood bank	POST /api/blood-bank/inventory/write-offs (Idempotency-Key)	Admin, Lab Staff
Staff	GET/POST /api/admin/staff, PATCH /api/admin/staff/{id}/active, /password	Admin
Donations	GET/POST /api/blood-bank/donations	Admin, Lab Staff, Nurse
Donations	PATCH /api/blood-bank/donations/{id}/screen	Admin, Lab Staff
Forecast	GET /api/blood-intelligence/forecast	see BloodIntelligenceController
Reports	GET /api/reports/daily	Admin

Consultation workflow:
1. A nurse calls the next waiting patient from Queue management. The API atomically selects the highest-priority/oldest waiting ticket and moves it to IN_PROGRESS.
2. The doctor opens Consultations, selects a patient in the Now serving list and reviews triage observations recorded for that queue visit.
3. The doctor saves a draft consultation. One consultation is associated with each queue ticket, while a patient can have multiple consultations across visits.
4. The doctor completes the consultation. The record becomes read-only and the associated queue ticket is marked COMPLETED in the same transaction.
5. Completed consultations remain available as patient history.


Prerequisites
Java JDK 21
Maven 3.8+
Node.js 18+ and npm
PostgreSQL 14+

## Run locally

### 1. Create the database (once)

In `psql` as the `postgres` user:

```sql
CREATE USER mbq_user WITH PASSWORD 'choose_a_password';
CREATE DATABASE mbq OWNER mbq_user;
```

Flyway creates all tables on the first start.

### 2. Set the environment variables

The backend will not start without `DB_PASSWORD` and `JWT_SECRET`. `JWT_SECRET` must be Base64 and decode to at least 32 bytes.

Windows PowerShell (for the current terminal window):

```powershell
$env:DB_PASSWORD = "choose_a_password"
$env:JWT_SECRET  = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

macOS / Linux / Git Bash:

```bash
export DB_PASSWORD=choose_a_password
export JWT_SECRET=$(openssl rand -base64 48)
```

Optional: `DB_URL` (default `jdbc:postgresql://localhost:5432/mbq`), `DB_USERNAME` (default `mbq_user`), `CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`).

### 3. Start the backend

```bash
cd Backend
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

The API listens on port 8081 (the frontend dev server proxies `/api` there). Set `SERVER_PORT` to change it, and `MBQ_API_TARGET` for the frontend proxy to match.

### 4. Start the frontend

```bash
cd Frontend
npm install
npm run dev
```

Open http://localhost:5173.

### Demo accounts

Created automatically on an empty database. Password for all: `Password123!` (development only).

| Username | Role | Department |
| --- | --- | --- |
| `admin` | Admin | none (system-wide) |
| `doctor`, `nurse`, `receptionist` | Doctor, Nurse, Receptionist | OPD |
| `lab_staff` | Lab staff (blood bank) | OPD |
| `emergency.doctor`, `emergency.nurse`, `emergency.receptionist` | Doctor, Nurse, Receptionist | Emergency |
| `maternity.doctor`, `maternity.nurse`, `maternity.receptionist` | Doctor, Nurse, Receptionist | Maternity |

### Tests

```bash
cd Backend
./mvnw clean test             # Windows: .\mvnw.cmd clean test
```

Unit tests always run. `MbqApplicationTests` and `PasswordVerificationTest` start the whole app, so they only run when `JWT_SECRET` is set and PostgreSQL is running; otherwise they are skipped.
