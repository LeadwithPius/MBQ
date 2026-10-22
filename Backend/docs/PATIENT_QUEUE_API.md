# Patient & Queue API

Covers FR1 (patient registration), FR2 (queue tickets), FR3 (live queue), FR4 (priority flagging),
plus "call next" and ticket status changes.

Base URL when running locally: `http://localhost:8080`

All requests and responses are JSON. Send `Content-Type: application/json` with every request body.

> **Security, temporary:** JWT login is not built yet, so these endpoints are open (`permitAll`) in
> `config/SecurityConfig.java`. The **Intended role** column shows who should be allowed once JWT lands.

---

## Endpoints at a glance

| Method | URL | What it does | Success | Intended role |
|---|---|---|---|---|
| POST | `/api/patients` | Register a patient | 201 | Receptionist |
| GET | `/api/patients/{id}` | Get one patient | 200 | All staff |
| GET | `/api/patients?name=` | Search by name (max 50) | 200 | Receptionist |
| POST | `/api/queue/tickets` | Issue a queue ticket | 201 | Receptionist |
| GET | `/api/queue/tickets/{id}` | Get one ticket | 200 | All staff |
| GET | `/api/queue/tickets?departmentId=&status=&date=` | List a day's tickets | 200 | All staff |
| GET | `/api/queue/departments/{departmentId}` | Live queue for a department | 200 | All staff |
| POST | `/api/queue/departments/{departmentId}/call-next` | Call the next patient | 200 / 204 | Triage Nurse |
| PATCH | `/api/queue/tickets/{id}/status` | Change ticket status | 200 | Nurse, Doctor |
| PATCH | `/api/queue/tickets/{id}/priority` | Flag priority / emergency | 200 | Triage Nurse |

---

## Allowed values

| Field | Values |
|---|---|
| `sex` | `MALE`, `FEMALE` (any letter case on input; stored upper case) |
| `bloodGroup` | `A_POS`, `A_NEG`, `B_POS`, `B_NEG`, `AB_POS`, `AB_NEG`, `O_POS`, `O_NEG` |
| `priorityLevel` | `NORMAL`, `URGENT`, `EMERGENCY` |
| `status` | `WAITING`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |

Enum values are **case-sensitive** and must be upper case, as shown.

---

## Patients

### POST /api/patients: register a patient

| Field | Required | Rules |
|---|---|---|
| `name` | yes | not blank, max 100 characters (leading/trailing spaces are trimmed) |
| `age` | yes | 0-130 |
| `sex` | yes | `MALE` or `FEMALE` |
| `contact` | no | 9-15 digits, optional leading `+` (e.g. `0971234567`, `+260971234567`) |
| `bloodGroup` | no | see allowed values; leave out if unknown |
| `facilityId` | no | must be an existing facility |

```json
{
  "name": "Mwaba Banda",
  "age": 34,
  "sex": "FEMALE",
  "contact": "+260971234567",
  "bloodGroup": "O_POS"
}
```

**201 Created**, with header `Location: http://localhost:8080/api/patients/1`

```json
{ "id": 1, "name": "Mwaba Banda", "age": 34, "sex": "FEMALE",
  "contact": "+260971234567", "bloodGroup": "O_POS", "facilityId": null }
```

Errors: **400** if validation fails, **404** if `facilityId` doesn't exist.

### GET /api/patients/{id}

**200** with the patient, or **404**.

### GET /api/patients?name=banda

Case-insensitive "contains" search, A-Z, at most 50 results.
Without `name`, returns the 50 most recently registered patients.

---

## Queue tickets

### POST /api/queue/tickets: issue a ticket

| Field | Required | Rules |
|---|---|---|
| `patientId` | yes | existing patient |
| `departmentId` | yes | existing department |
| `priorityLevel` | no | defaults to `NORMAL` |
| `requiresBlood` | no | defaults to `false` |

```json
{ "patientId": 1, "departmentId": 1 }
```

**201 Created**

```json
{
  "id": 1,
  "ticketNumber": "OPD-001",
  "patientId": 1,
  "patientName": "Mwaba Banda",
  "departmentId": 1,
  "departmentName": "OPD",
  "priorityLevel": "NORMAL",
  "status": "WAITING",
  "timeIssued": "2026-10-22T09:15:02.123456",
  "triageNotes": null,
  "outcome": null,
  "requiresBlood": false,
  "position": 1
}
```

**Ticket numbers:** the first 3 letters of the department name plus a daily counter
(`OPD-001`, `OPD-002`, `EME-001`...). The counter restarts at 001 each day. Creation locks the
department row, so two receptionists clicking at the same moment can't get the same number.

Errors:
- **400**: missing `patientId`/`departmentId`, or an invalid `priorityLevel`
- **404**: patient or department doesn't exist
- **409**: the patient already has an active (`WAITING` or `IN_PROGRESS`) ticket

### GET /api/queue/tickets/{id}

**200** with the ticket, or **404**. `position` is the patient's place in line (1 = next),
and `null` if the ticket isn't `WAITING`.

### GET /api/queue/tickets?departmentId=1&status=WAITING&date=2026-10-22

All filters are optional. `date` (format `YYYY-MM-DD`) defaults to **today**.
Results are ordered by time issued.

### GET /api/queue/departments/{departmentId}: live queue (dashboard)

```json
{
  "departmentId": 1,
  "departmentName": "OPD",
  "waitingCount": 2,
  "nowServing": [ { "ticketNumber": "OPD-003", "status": "IN_PROGRESS", "position": null } ],
  "waiting": [
    { "ticketNumber": "OPD-005", "priorityLevel": "EMERGENCY", "position": 1 },
    { "ticketNumber": "OPD-004", "priorityLevel": "NORMAL",    "position": 2 }
  ]
}
```
(Ticket objects are shortened here; each one has all the ticket fields shown above.)

**Call order:** `EMERGENCY` → `URGENT` → `NORMAL`; within the same priority, whoever arrived first.

For NFR2 (dashboard updates within 5 seconds), the frontend should poll this endpoint every 3-5 seconds.

### POST /api/queue/departments/{departmentId}/call-next

Moves the first waiting ticket (by call order) to `IN_PROGRESS`.
- **200** with the called ticket
- **204 No Content** if nobody is waiting
- **404** if the department doesn't exist

The department row is locked, so two nurses pressing the button together can't call the same patient.

### PATCH /api/queue/tickets/{id}/status

```json
{ "status": "COMPLETED", "outcome": "Prescribed antibiotics, discharged" }
```

| Field | Required | Rules |
|---|---|---|
| `status` | yes | new status |
| `outcome` | no | max 255 characters (doctor's consultation outcome) |
| `triageNotes` | no | max 255 characters |

Allowed changes (anything else returns **409 Conflict**):

```
WAITING ──► IN_PROGRESS ──► COMPLETED
   │            │  │
   │            │  └──► WAITING   (sent back to wait, e.g. for lab results)
   ▼            ▼
CANCELLED   CANCELLED

COMPLETED and CANCELLED are final.
```

Once a ticket is `COMPLETED` or `CANCELLED`, the patient can be given a new ticket.

### PATCH /api/queue/tickets/{id}/priority: triage flag (FR4)

```json
{ "priorityLevel": "EMERGENCY", "triageNotes": "Severe bleeding", "requiresBlood": true }
```

| Field | Required | Rules |
|---|---|---|
| `priorityLevel` | yes | new priority |
| `triageNotes` | no | max 255 characters |
| `requiresBlood` | no | `true` flags the emergency blood branch |

Only allowed while the ticket is `WAITING` or `IN_PROGRESS`, otherwise **409**.
The response's `position` shows where the ticket now sits in the queue.

---

## Error format

Every error uses the same shape:

```json
{
  "timestamp": "2026-10-22T07:15:02.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/patients",
  "fieldErrors": {
    "name": "Name is required",
    "age": "Age cannot be negative"
  }
}
```

| Status | Meaning |
|---|---|
| 400 | Invalid input: failed validation (see `fieldErrors`), malformed JSON, unknown enum value, wrong parameter type |
| 404 | Patient / ticket / department / facility not found |
| 409 | Valid request that breaks a queue rule (invalid status change, duplicate active ticket, finished ticket) |
| 500 | Unexpected server error (details are logged, never sent to the client) |

---

## How to test

### 1. Automated tests (no PostgreSQL needed)

The tests start the whole app against an **in-memory H2 database**. Flyway runs the real migrations,
and `DataSeeder` adds the demo facility plus the OPD / Emergency / Maternity departments.

From the `Backend` folder:

```bash
# Windows (PowerShell)
.\mvnw.cmd test

# macOS / Linux / Git Bash
./mvnw test
```

Expect `BUILD SUCCESS` and `Tests run: ..., Failures: 0, Errors: 0`.

| Test class | What it covers |
|---|---|
| `PatientApiTest` | registration, validation errors, 404s, search |
| `QueueApiTest` | ticket numbers, duplicates, live queue order, call-next, status rules, priority |
| `QueueRulesTest` | call order and every allowed/forbidden status change (no database) |
| `TicketNumberGeneratorTest` | prefixes and number formatting (no database) |

GitHub also runs all the tests automatically on every push and pull request
(`.github/workflows/backend-ci.yml`).

### 2. Manually, against the running app

You need PostgreSQL running with the database from `application.yml`
(default: database `mbq_db`, user `mbq_user`, password `change_me`).

```bash
.\mvnw.cmd spring-boot:run
```

The seeder creates departments with ids 1 = OPD, 2 = Emergency, 3 = Maternity on a fresh database.

**Easiest:** open `Backend/http/patient-queue.http` in VS Code with the **REST Client** extension
(by Huachao Mao) and click **Send Request** above each request, top to bottom.

**Or with curl.** In PowerShell, type `curl.exe`, not `curl`, because plain `curl` is an alias for a different command:

```bash
curl.exe -X POST http://localhost:8080/api/patients -H "Content-Type: application/json" -d "{\"name\":\"Mwaba Banda\",\"age\":34,\"sex\":\"FEMALE\"}"
curl.exe -X POST http://localhost:8080/api/queue/tickets -H "Content-Type: application/json" -d "{\"patientId\":1,\"departmentId\":1}"
curl.exe http://localhost:8080/api/queue/departments/1
```
