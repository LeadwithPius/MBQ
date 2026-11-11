import hmac
import math
import os
import random
from datetime import date, timedelta
from typing import List

import numpy as np
import xgboost as xgb
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

MODEL_VERSION = "xgb-facility-demand-v2"
HISTORY_DAYS = 365
HOLDOUT_DAYS = 28
FORECAST_DAYS = 30
MIN_TRAIN_ROWS = 120
MIN_ACTIVE_DAYS = 20

app = FastAPI(title="MBQ Blood Intelligence ML", version="2.0.0")


class DailyDemand(BaseModel):
    date: date
    unitsIssued: int = Field(ge=0)


class SeriesInput(BaseModel):
    stockId: int
    facilityId: int
    bloodGroup: str
    dailyDemand: List[DailyDemand] = Field(min_length=29, max_length=730)


class ForecastRequest(BaseModel):
    series: List[SeriesInput] = Field(min_length=1, max_length=1000)


def _features(values, day, group_code, group_count, elapsed_days):
    dow = day.weekday()
    doy = day.timetuple().tm_yday
    recent7 = values[-7:]
    recent14 = values[-14:]
    recent28 = values[-28:]
    categorical = [1.0 if i == group_code else 0.0 for i in range(group_count)]
    return categorical + [
        math.sin(2 * math.pi * dow / 7),
        math.cos(2 * math.pi * dow / 7),
        math.sin(2 * math.pi * doy / 365.25),
        math.cos(2 * math.pi * doy / 365.25),
        float(elapsed_days),
        float(values[-1]), float(values[-2]), float(values[-7]),
        float(values[-14]), float(values[-28]),
        float(np.mean(recent7)), float(np.mean(recent14)), float(np.mean(recent28)),
        float(np.std(recent7)), float(np.mean(recent7) - np.mean(recent28)),
    ]


def _prepare(payload):
    if len({item.facilityId for item in payload.series}) != 1:
        raise HTTPException(status_code=422, detail="A forecast request must contain one facility only.")
    groups = sorted({item.bloodGroup for item in payload.series})
    group_map = {value: index for index, value in enumerate(groups)}
    prepared = []
    for item in payload.series:
        rows = sorted(item.dailyDemand, key=lambda row: row.date)
        if len({row.date for row in rows}) != len(rows):
            raise HTTPException(status_code=422, detail=f"Duplicate dates for stock {item.stockId}")
        if any((rows[i].date - rows[i - 1].date).days != 1 for i in range(1, len(rows))):
            raise HTTPException(status_code=422, detail=f"Daily history for stock {item.stockId} has date gaps")
        prepared.append({
            "stock_id": item.stockId,
            "facility_id": item.facilityId,
            "blood_group": item.bloodGroup,
            "group_code": group_map[item.bloodGroup],
            "dates": [row.date for row in rows],
            "values": [float(row.unitsIssued) for row in rows],
        })
    return prepared, groups


def _mae(actual, predicted):
    return float(np.mean(np.abs(np.asarray(actual, dtype=float) - np.asarray(predicted, dtype=float))))


def _make_model():
    return xgb.XGBRegressor(
        objective="reg:squarederror",
        eval_metric="mae",
        n_estimators=420,
        max_depth=4,
        learning_rate=0.035,
        subsample=0.9,
        colsample_bytree=0.9,
        reg_lambda=2.5,
        min_child_weight=2,
        n_jobs=2,
        random_state=42,
    )


def _build_forecast(payload, demo_metadata=None):
    series, groups = _prepare(payload)
    latest_date = max(item["dates"][-1] for item in series)
    train_cutoff = latest_date - timedelta(days=HOLDOUT_DAYS)
    train_x, train_y, valid_x, valid_y, baseline_y = [], [], [], [], []

    for item in series:
        values = item["values"]
        dates = item["dates"]
        for index in range(28, len(values)):
            day = dates[index]
            features = _features(values[:index], day, item["group_code"], len(groups), index)
            if day <= train_cutoff:
                train_x.append(features)
                train_y.append(values[index])
            else:
                valid_x.append(features)
                valid_y.append(values[index])
                baseline_y.append(float(np.mean(values[index - 28:index])))

    active_days = sum(1 for value in train_y if value > 0)
    if len(train_x) < MIN_TRAIN_ROWS or active_days < MIN_ACTIVE_DAYS or len(valid_x) < 20:
        return {
            "status": "INSUFFICIENT_DATA",
            "modelVersion": MODEL_VERSION,
            "message": "Insufficient history for a reliable facility-specific model evaluation.",
            "forecasts": [],
            "facilityForecasts": [],
            "synthetic": bool(demo_metadata),
            "dataSource": "SYNTHETIC_DEMO" if demo_metadata else "FACILITY_LEDGER",
        }

    model = _make_model()
    model.fit(np.asarray(train_x, dtype=np.float32), np.asarray(train_y, dtype=np.float32))
    xgb_valid = np.maximum(0, model.predict(np.asarray(valid_x, dtype=np.float32)))
    xgb_mae = _mae(valid_y, xgb_valid)
    baseline_mae = _mae(valid_y, baseline_y)
    improvement = ((baseline_mae - xgb_mae) / baseline_mae * 100.0) if baseline_mae > 0 else 0.0
    xgb_wins = xgb_mae < baseline_mae * 0.98
    selected_method = "XGBOOST" if xgb_wins else "MOVING_AVERAGE"

    # Refit on all observed history after the chronological holdout evaluation.
    all_x, all_y = [], []
    for item in series:
        values = item["values"]
        for index in range(28, len(values)):
            all_x.append(_features(values[:index], item["dates"][index],
                                   item["group_code"], len(groups), index))
            all_y.append(values[index])
    model.fit(np.asarray(all_x, dtype=np.float32), np.asarray(all_y, dtype=np.float32))

    facility_forecasts = []
    legacy_forecasts = []
    for item in series:
        values = list(item["values"])
        future_dates = []
        predictions = []
        next_day = item["dates"][-1] + timedelta(days=1)
        for offset in range(FORECAST_DAYS):
            day = next_day + timedelta(days=offset)
            future_dates.append(day)
            features = _features(values, day, item["group_code"], len(groups),
                                 len(item["values"]) + offset)
            xgb_prediction = max(0.0, float(model.predict(
                np.asarray([features], dtype=np.float32)
            )[0]))
            baseline_prediction = float(np.mean(values[-28:]))
            prediction = xgb_prediction if xgb_wins else max(0.0, baseline_prediction)
            predictions.append(prediction)
            values.append(prediction)

        # Demo stock is deliberately independent from the demand training data.
        # Live callers can supply current stock in future versions; the synthetic
        # scenario uses a controlled opening balance to demonstrate early warnings.
        current_stock = (demo_metadata or {}).get("openingStock", {}).get(item["blood_group"], 0)
        cumulative = np.cumsum(predictions)
        projected = [max(0, int(math.floor(current_stock - cumulative[n - 1]))) for n in (7, 14, 30)]
        stockout_index = next((i for i, total in enumerate(cumulative) if total >= current_stock), None)
        stockout_date = future_dates[stockout_index] if stockout_index is not None else None
        days_to_stockout = stockout_index + 1 if stockout_index is not None else None
        if stockout_index is None:
            risk = "LOW"
        elif days_to_stockout <= 7:
            risk = "CRITICAL"
        elif days_to_stockout <= 14:
            risk = "HIGH"
        else:
            risk = "MODERATE"
        demand7 = int(math.ceil(sum(predictions[:7])))
        demand14 = int(math.ceil(sum(predictions[:14])))
        demand30 = int(math.ceil(sum(predictions)))
        safety_buffer = int(math.ceil(demand7 * 0.5))
        recommended = max(0, demand30 + safety_buffer - current_stock)
        history_start = max(0, len(item["dates"]) - 90)
        historical = [
            {"date": d.isoformat(), "units": int(v)}
            for d, v in zip(item["dates"][history_start:], item["values"][history_start:])
        ]
        forecast_points = [
            {"date": d.isoformat(), "units": round(float(v), 2)}
            for d, v in zip(future_dates, predictions)
        ]
        recent_average = float(np.mean(item["values"][-7:]))
        historical_average = float(np.mean(item["values"][-28:]))
        explanation = (
            f"Recent 7-day average usage is {recent_average:.1f} units/day versus "
            f"{historical_average:.1f} units/day across the last 28 days. "
            f"The selected {selected_method} forecast estimates {demand30} units of "
            f"demand over 30 days."
        )
        facility_forecasts.append({
            "bloodGroup": item["blood_group"],
            "currentStockUnits": int(current_stock),
            "averageDailyDemand": round(historical_average, 2),
            "recent7DayAverage": round(recent_average, 2),
            "predictedDemand7Days": demand7,
            "predictedDemand14Days": demand14,
            "predictedDemand30Days": demand30,
            "projectedStock7Days": projected[0],
            "projectedStock14Days": projected[1],
            "projectedStock30Days": projected[2],
            "estimatedStockoutDate": stockout_date.isoformat() if stockout_date else None,
            "daysUntilStockout": days_to_stockout,
            "riskLevel": risk,
            "recommendedReplenishmentUnits": recommended,
            "historicalDemand": historical,
            "forecastDemand": forecast_points,
            "explanation": explanation,
        })
        legacy_forecasts.append({
            "stockId": item["stock_id"],
            "predictedDemand7Days": demand7,
            "predictedDemand14Days": demand14,
        })

    result = {
        "status": "TRAINED" if xgb_wins else "BASELINE_WINS",
        "modelVersion": MODEL_VERSION,
        "validationMae": round(xgb_mae, 4),
        "baselineMae": round(baseline_mae, 4),
        "improvementPercent": round(improvement, 2),
        "trainingRows": len(train_x),
        "validationRows": len(valid_x),
        "trainingStart": min(item["dates"][0] for item in series).isoformat(),
        "trainingEnd": train_cutoff.isoformat(),
        "validationStart": (train_cutoff + timedelta(days=1)).isoformat(),
        "validationEnd": latest_date.isoformat(),
        "holdoutDays": HOLDOUT_DAYS,
        "selectedForecastMethod": selected_method,
        "facilityName": (demo_metadata or {}).get("facilityName", "Selected facility"),
        "facilityId": series[0]["facility_id"],
        "seriesCount": len(series),
        "facilityCount": 1,
        "bloodGroups": len(groups),
        "historyDays": min(len(item["dates"]) for item in series),
        "dataSource": "SYNTHETIC_DEMO" if demo_metadata else "FACILITY_LEDGER",
        "synthetic": bool(demo_metadata),
        "message": (
            "XGBoost passed chronological holdout validation and is used for the 30-day forecast."
            if xgb_wins else
            "XGBoost was evaluated, but the 28-day moving-average baseline performed better. "
            "The report uses the baseline forecast and shows both model scores transparently."
        ),
        "notice": (
            "Synthetic demonstration data only. This single-facility scenario is not actual "
            "hospital usage, stock, patients or clinical demand."
            if demo_metadata else
            "Forecasts are based on the selected facility's recorded issue history."
        ),
        "facilityForecasts": facility_forecasts,
        "forecasts": legacy_forecasts,
        "featureSummary": [
            "Blood-group identity",
            "Day-of-week and annual seasonal cycles",
            "Lagged consumption (1, 2, 7, 14 and 28 days)",
            "7-, 14- and 28-day rolling averages",
            "Recent demand variability and trend",
        ],
    }
    return result


def _authorize(x_api_key):
    expected_key = os.getenv("ML_API_KEY", "")
    if expected_key and not hmac.compare_digest(x_api_key or "", expected_key):
        raise HTTPException(status_code=401, detail="Invalid ML service API key")


@app.get("/health")
def health():
    return {"status": "UP", "model": MODEL_VERSION}


@app.post("/forecast")
def forecast(payload: ForecastRequest, x_api_key: str | None = Header(default=None)):
    _authorize(x_api_key)
    return _build_forecast(payload)


@app.post("/demo-forecast")
def demo_forecast(x_api_key: str | None = Header(default=None)):
    """One-facility synthetic POC. It never reads or writes hospital data."""
    _authorize(x_api_key)
    rng = random.Random(20261111)
    today = date.today()
    start = today - timedelta(days=HISTORY_DAYS - 1)
    facility_id = 101
    groups = [
        ("O_POS", 6.4, 95), ("O_NEG", 2.0, 20),
        ("A_POS", 4.5, 85), ("A_NEG", 1.3, 28),
        ("B_POS", 3.2, 75), ("B_NEG", 1.0, 20),
        ("AB_POS", 1.8, 55), ("AB_NEG", 0.7, 15),
    ]
    series = []
    for index, (group, base, opening_stock) in enumerate(groups):
        rows = []
        for offset in range(HISTORY_DAYS):
            day = start + timedelta(days=offset)
            weekday = [1.18, 1.12, 1.08, 1.02, 0.98, 0.70, 0.62][day.weekday()]
            annual = 1.0 + 0.13 * math.sin(2 * math.pi * (day.timetuple().tm_yday - 35) / 365.25)
            growth = 1.0 + 0.0007 * offset
            recurring = 1.0 + (0.22 if offset % 49 in (12, 13, 14) else 0.0)
            noise = rng.uniform(0.90, 1.10)
            demand = max(0, int(round(base * weekday * annual * growth * recurring * noise)))
            rows.append(DailyDemand(date=day, unitsIssued=demand))
        series.append(SeriesInput(
            stockId=900001 + index,
            facilityId=facility_id,
            bloodGroup=group,
            dailyDemand=rows,
        ))

    payload = ForecastRequest(series=series)
    metadata = {
        "facilityName": "MBQ Demonstration Hospital",
        "openingStock": {group: stock for group, _, stock in groups},
    }
    result = _build_forecast(payload, metadata)
    result["reportType"] = "FACILITY_PREDICTIVE_MANAGEMENT_REPORT"
    result["notice"] = (
        "SYNTHETIC DEMONSTRATION ONLY — MBQ Demonstration Hospital is fictional. "
        "The 365-day usage history and opening stock are generated for this proof of concept."
    )
    return result
