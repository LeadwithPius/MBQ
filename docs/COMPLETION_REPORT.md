# MBQ — Final completion report

Branch: `integration/final-completion` (based on `main` at `2420c8b`)
Date: 2026-11-06

## What this branch contains

1. **Bruce's two open branches merged in, unchanged:**
   - `feature/doctor-consultation-nurse-queue` (V9 consultation)
   - `feature/blood-intelligence-stages-1-4` (V10 batch verification, donor facility, forecast)

   Both merged cleanly with no conflicts.
2. **New work on top**, using new migrations V11 and V12. No applied migration was edited.
   - **Laboratory module (Workflow B):** V11 `lab_request`, service, API, Laboratory page, and a lab panel in Consultations.
   - **Blood donations (Workflow C):** V12 `blood_donation`. Only donations that pass screening enter stock. They go in through Bruce's receive path as `PENDING_VERIFICATION` batches.
   - **ABO/RhD compatibility check when issuing blood for a patient (Workflow D).**
   - **Self-service password change.**
   - **Fixes:**
     - Seeded donors now get a facility.
     - V12 backfills `donor.facility_id` on single-facility databases.
     - The backend port is aligned with the frontend proxy (8081).
     - The README lists the right Java version (21).

## Verification: what was and wasn't run

| Check | Result |
| --- | --- |
| Java syntax parse of all 100 backend source/test files | ✅ Run, 0 errors |
| Backend compile + `mvn test` | ⚠️ **Not run here.** Maven Central is blocked in the build sandbox. **Run `.\mvnw.cmd clean test` before merging.** |
| Frontend `npm run lint` (oxlint) | ✅ Run, clean |
| Frontend `vite build` | ✅ Run, builds |
| Browser smoke test with mocked API | ✅ Run, all steps passed. Lab staff: start test → enter result → completed list; record donation → screen passed. Wrong then right password change. Doctor: request test → result visible; previous results shown; Laboratory menu hidden. |
| End-to-end against a real PostgreSQL | ⚠️ **Not run here.** Do this on a laptop (checklist below). |

New unit tests (Mockito, no database):
- `LabServiceTest`: 6 tests
- `DonationServiceTest`: 7 tests
- `BloodCompatibilityTest`: 3 tests
- `BloodInventoryServiceTest`: 2 tests
- `AccountServiceTest`: 3 tests

They are written but have **not been executed yet**.

## Module status

| Module | Status | Notes |
| --- | --- | --- |
| Auth / JWT / RBAC | COMPLETE | Login, BCrypt, stateless JWT, `@PreAuthorize` on every controller. Self password change added. |
| Staff account management | COMPLETE | Admin "Staff accounts" page: create (non-admins need a department, username unique), deactivate/reactivate (not yourself; deactivated users are locked out immediately), reset password. Accounts are never deleted. |
| Patients | COMPLETE | Register/search, facility-scoped. |
| Queue / triage | COMPLETE | Priority ordering, call-next with a row lock, duplicate-ticket check, triage observations. |
| Queue concurrency (Workflow F) | NEEDS VERIFICATION | Pessimistic lock is in place. No concurrent load test exists. |
| Doctor consultation | COMPLETE | Bruce's V9 work: draft → complete, read-only after completion, completes the ticket. |
| Laboratory | COMPLETE (new) | Request only from an open consultation, by its doctor. Duplicate open test → 409 (also a DB unique index). Lab worklist is urgent-first and facility-scoped. Start → result → **immutable**. Cancel needs a reason. Doctor sees results (polls every 10 s) and previous results. |
| Blood bank: receive / verify / issue | COMPLETE | Idempotency-Key, FEFO, never negative, only verified + unexpired units issuable. |
| Blood bank: compatibility | COMPLETE (new) | Optional `patientId` on issue. Incompatible group → 409 before any stock change. Unknown patient group → O− only. |
| Blood bank: expire / waste | COMPLETE | "Write off" on expired batches removes all remaining units; "Record wastage" removes N units from any batch. Idempotent, locked, never negative, recorded as EXPIRED/WASTED movements. |
| Blood bank: transfer / manual adjustment | NOT BUILT | Out of scope for the demo (single facility). Enum values exist for later. |
| Thresholds + low-stock alert (Workflow E) | COMPLETE | Per-group threshold, low flag on issuable units, simulated donor alerts. |
| Donors | COMPLETE | Facility-scoped (V10). Seeder fixed. |
| Donations (Workflow C) | COMPLETE (new) | Eligible donor only, 56-day interval, 1–2 units. Screen PASS → batch `DON-<id>`, expiry = collection + 35 days, `PENDING_VERIFICATION`. FAIL → no stock change. Each donation can be screened once. |
| Reports | COMPLETE | Daily report now also shows units written off, donations collected/passed/failed, and lab tests requested/completed. |
| AI readiness | COMPLETE for blood forecast; NEEDS VERIFICATION for clinical risk | Forecast is a transparent 28-day baseline and returns INSUFFICIENT_DATA instead of guessing. The clinical risk assessment provider was not reviewed in this pass. |
| Frontend UX | COMPLETE for the flows above | Role-based menu. Lab staff land on Laboratory. |
| Database quality | COMPLETE | FKs, CHECK constraints (result only when completed; donation status ↔ batch), indexes. `ddl-auto=validate` mappings checked by hand against V11/V12. |
| Security | MOSTLY COMPLETE | Facility/department scoping on all new endpoints. No login rate limiting. Demo passwords are documented as dev-only. |
| Tests | PARTIAL | Unit tests for every service rule. No DB integration tests (Testcontainers). |

## How to merge (for whoever owns the repo)

This branch contains Bruce's two branches. Merging it into `main` brings in his commits as well, so merge his PRs first (or close them as superseded). Migrations go V9 → V10 → V11 → V12. A database already at V10 upgrades cleanly.

## Manual end-to-end checklist (real database)

Run the backend with `DB_PASSWORD` and `JWT_SECRET` set, and the frontend with `npm run dev`. Then:

1. **receptionist:** register a patient → ticket issued.
2. **nurse:** call next → patient moves to Now serving; record triage.
3. **doctor:** Consultations → select patient → save draft → request "Full blood count" (Urgent). Requesting it again must give 409.
4. **lab_staff:** Laboratory → urgent banner → Start → Enter result (flag abnormal) → it appears under Completed.
5. **doctor:** the result appears in the consultation within 10 s → complete the consultation.
6. **lab_staff:** Blood bank → Donations → record a donation for Bupe Mulenga.
   - Recording a second one must be refused (56 days).
   - Mark it Passed → batch DON-n shows PENDING VERIFICATION → Verify batch → issuable units increase.
7. **lab_staff:** issue O+ for a patient whose group is O−. This must be refused with "Incompatible blood".
8. **any user:** key icon → change password → sign out → sign in with the new password.
