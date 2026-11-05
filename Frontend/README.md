# MBQ frontend

The React application has a public landing page and a JWT-protected staff workspace.

## Run locally

1. Start PostgreSQL and the Spring Boot backend on port 8081.
2. From this directory run:

   ```bash
   npm install
   npm run dev
   ```

3. Open http://localhost:5173.

Vite proxies requests beginning with `/api` to `http://localhost:8081`. Override the target with `MBQ_API_TARGET` if needed.

For a separately hosted API, set `VITE_API_BASE_URL` to the full API base, including `/api`, for example `https://api.example.org/api`.

## Authentication

The login page calls `POST /api/auth/login`. The access token is stored in session storage and sent as a Bearer token. It is cleared on logout, token expiry, or an API 401 response. The application does not implement refresh tokens because the backend does not currently issue them.

## Connected API modules

- Authentication: `POST /api/auth/login`
- Patients: `GET/POST /api/patients`
- Queue: `GET /api/queue/departments/{departmentId}/waiting`, `POST /api/queue/tickets`, `POST /api/queue/departments/{departmentId}/call-next`, `PATCH /api/queue/tickets/{ticketId}/status`
- Triage: observation creation, patient observation lookup, assessment lookup and clinician review under `/api/triage`

The patient-list and queue-ticket-creation endpoints were added in this integration branch. Patient listing is scoped to the authenticated staff member's facility. Queue-ticket creation and patient registration enforce facility scope for non-admin staff.

Blood-bank and reporting screens remain explicitly marked as pending because those backend APIs are not yet exposed. They do not display mock records or invented metrics.

## Before production

- Set `VITE_API_BASE_URL` to the deployed API base.
- Set backend `CORS_ALLOWED_ORIGINS` to the exact deployed frontend origin(s), comma-separated.
- Serve the frontend and API over HTTPS.
- Run `npm install` after pulling this branch to synchronize `package-lock.json` with the added React Router dependency, then run `npm run build`.
