# Blood intelligence implementation (Stages 1–4)

## Current implementation

- Low-stock flags on the blood-bank stock endpoint now compare the threshold with verified, unexpired, issuable units rather than the aggregate physical balance.
- Newly received batches enter `PENDING_VERIFICATION`; they are excluded from FEFO issuing until a lab/admin user verifies them through `PATCH /api/blood-bank/inventory/batches/{batchId}/verify`.
- Donor records now support a facility association. Non-admin donor listing and donor mobilisation are scoped to the authenticated staff facility. Existing donor rows remain unassigned after migration and are not visible to non-admin facility users until reconciled by an administrator.
- `GET /api/blood-intelligence/forecast` returns a transparent 28-calendar-day issue-history baseline for each visible stock record. It includes usable stock, daily average demand, 7/14-day expected demand, estimated stock-out days, risk and a replenishment estimate.
- The Blood Bank page displays this forecast and exposes batch verification for lab/admin users.

## Forecast methodology and safety limits

The first version is deliberately explainable, not an ML model. It sums `ISSUED` ledger units in the previous 28 days and divides by 28. It requires at least three distinct days with issue activity before showing a forecast. It uses verified, unexpired batch balances as usable stock.

The replenishment estimate is `max(0, 14-day forecast demand - usable stock)`. Risk bands are based on estimated days until stock-out: critical (<=3), high (<=7), moderate (<=14), low (>14). When history is insufficient, the API returns `INSUFFICIENT_DATA` and does not invent a forecast.

This is a planning aid, not a clinical recommendation. Validate the baseline against local data before operational use.

## Remaining work before production

1. Complete a first-class blood request lifecycle (requested, approved, allocated, fulfilled, cancelled, unfulfilled) and record unmet demand. The current baseline uses fulfilled issue movements only and can understate demand.
2. Add an administrator workflow to assign/reconcile legacy donors whose `facility_id` is null, and a facility selector for administrators creating donors.
3. Add stock reconciliation reports and controlled compensating adjustments.
4. Expand verification audit metadata (verifier, timestamp, checks performed) and separation-of-duties policy.
5. Add forecasting backtests, error metrics, seasonality, confidence intervals, minimum-history policy and model versioning before replacing the baseline with a trained model.
6. Add integration tests against PostgreSQL for V10, facility isolation, batch verification and concurrent inventory operations.
7. Add real notification-provider integration only after donor consent, delivery tracking, retry and opt-out are implemented.

## Local verification

Run backend tests and frontend lint/build after pulling this branch. Apply Flyway V10 on a test database first; do not use a production database for initial verification.
