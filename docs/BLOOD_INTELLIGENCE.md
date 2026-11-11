# Blood intelligence and XGBoost forecasting

## Current implementation

- Low-stock flags use verified, unexpired, issuable units rather than the aggregate physical balance.
- Newly received batches enter `PENDING_VERIFICATION` and are excluded from FEFO issuing until verified.
- `GET /api/blood-intelligence/forecast` now supports two forecast methods:
  - **XGBoost regression** from the optional Python ML service, using up to 365 days of daily `ISSUED` ledger history per facility and blood group.
  - **28-day moving-average fallback** in the Java backend whenever the ML service is not configured, unavailable, short of data, or fails the validation comparison.
- XGBoost uses calendar features (day of week and annual seasonality), lags (1, 7, 14, 28 days), rolling means (7, 14, 28 days), facility and blood-group indicators.
- The model is trained as a pooled regression model across the stock series included in the authenticated user's facility scope. It uses `reg:squarederror`.
- A chronological 14-day holdout is compared with the moving-average baseline. XGBoost is selected only when its holdout MAE is at least 2% lower than the baseline MAE. Otherwise, the API returns the Java baseline.
- The API response includes `forecastMethod`, `modelVersion`, and `validationMae`. The Blood Bank page displays the selected method and validation MAE.

## Deploying the ML service

The Python service is independent of the Spring Boot backend and is located in `ml-service/`.

1. Create a new Render **Private Service** (or web service if private services are unavailable) from this repository.
2. Set the root directory to `ml-service`, runtime to **Docker**, and Dockerfile path to `Dockerfile`.
3. Add a strong random `ML_API_KEY` environment variable to the Python service.
4. Deploy it and copy its internal service URL (preferred when the backend can reach it).
5. In the existing Render MBQ backend service, add:
   - `BLOOD_INTELLIGENCE_ML_URL` = the ML service URL, without a trailing slash.
   - `BLOOD_INTELLIGENCE_ML_API_KEY` = the exact same secret used by the ML service.
6. Redeploy the backend after saving its environment variables.

The ML service exposes `GET /health` and `POST /forecast`. The backend sends daily issue totals; the Python service does not connect directly to Neon and does not receive database credentials. If the ML service is down or its URL is blank, blood-bank forecasting continues with the Java baseline.

For local use, run the service from `ml-service/` with `python -m pip install -r requirements.txt` and `uvicorn app:app --reload --port 8000`. Set `ML_API_KEY` in the Python process and set `BLOOD_INTELLIGENCE_ML_URL=http://localhost:8000` plus the matching `BLOOD_INTELLIGENCE_ML_API_KEY` for the backend.

## Model eligibility and limitations

- XGBoost needs at least 120 training rows, 20 training days with non-zero issues, and 20 chronological validation rows. Insufficient data results in the baseline, not fabricated ML output.
- The model is retrained when a forecast request reaches the ML service; the current implementation does not persist a trained model artifact between requests.
- The source ledger records fulfilled `ISSUED` movements, not every requested unit. Unfulfilled or cancelled requests are not yet included, so the forecast is a prediction of recorded fulfilled consumption and can understate unmet demand.
- The model is a planning aid. It must not authorize blood issuance, replace compatibility checks, override FEFO, bypass verification, or change low-stock safety rules.
- Do not claim that XGBoost is active until the ML service has been deployed, the backend environment variables have been configured, and the API response reports `forecastMethod: "XGBOOST"`.

## Existing blood-bank safeguards

- Only verified and unexpired batches contribute to usable inventory.
- FEFO issuing and ABO/RhD compatibility checks remain authoritative.
- Low-stock thresholds and inventory operations remain independent of the forecasting model.
- Forecasts do not make clinical decisions or automatically issue or order blood.

## Next steps before operational reliance

1. Capture the full blood-request lifecycle, including unmet demand, so forecasts represent demand rather than only fulfilled consumption.
2. Run rolling-origin backtests over multiple time windows and blood groups; monitor MAE and bias after deployment.
3. Persist model artifacts and training metadata in durable storage if training latency or reproducibility requires it.
4. Add integration tests for the Python service, service authentication, timeout/fallback behavior, and PostgreSQL data access.
5. Reconcile legacy donor facility assignments and complete the existing audit/notification work.


## Synthetic presentation mode

The Blood Bank dashboard includes a **Run synthetic ML demo** action. It calls the authenticated backend route `GET /api/blood-intelligence/demo-forecast`, which proxies to the Python service's `POST /demo-forecast` route.

- Generates deterministic, synthetic daily issue histories for 3 simulated facilities, 8 blood groups and 365 days (24 stock series).
- Includes weekday effects, gradual demand changes, annual seasonality, periodic campaign spikes and deterministic noise.
- Runs the same XGBoost training and chronological holdout comparison as operational forecasting.
- Returns a `synthetic: true` marker and `dataSource: "SYNTHETIC_DEMO"`; the UI labels the output as demo data.
- Does not read from or write to Neon, create inventory movements, or alter patients, batches, donors or live stock.
- The demo endpoint uses the same ML API key and staff authentication as the normal blood-intelligence routes.

For a presentation, explain that the sample is generated to demonstrate the pipeline and algorithm. Its MAE is a proof-of-concept metric on synthetic data, not evidence of accuracy on real hospital demand. The demo endpoint is intended for controlled presentations and should be disabled or access-restricted in a production environment if it is not needed.
