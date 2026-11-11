import hmac
import math
import os
from datetime import date, timedelta
from typing import List

import numpy as np
import xgboost as xgb
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

MODEL_VERSION = "xgb-blood-demand-v1"
MIN_TRAIN_ROWS = 120
MIN_ACTIVE_DAYS = 20
HOLDOUT_DAYS = 14

app = FastAPI(title="MBQ Blood Intelligence ML", version="1.0.0")


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


def _features(values, day, facility_code, group_code, facility_count, group_count):
    dow = day.weekday()
    doy = day.timetuple().tm_yday
    categorical = (
        [1.0 if i == facility_code else 0.0 for i in range(facility_count)]
        + [1.0 if i == group_code else 0.0 for i in range(group_count)]
    )
    recent = values[-28:]
    return categorical + [
        math.sin(2 * math.pi * dow / 7), math.cos(2 * math.pi * dow / 7),
        math.sin(2 * math.pi * doy / 365.25), math.cos(2 * math.pi * doy / 365.25),
        float(values[-1]), float(values[-7]), float(values[-14]), float(values[-28]),
        float(np.mean(recent[-7:])), float(np.mean(recent[-14:])), float(np.mean(recent)),
    ]


def _prepare(payload):
    series = []
    facility_ids = sorted({s.facilityId for s in payload.series})
    groups = sorted({s.bloodGroup for s in payload.series})
    facility_map = {value: index for index, value in enumerate(facility_ids)}
    group_map = {value: index for index, value in enumerate(groups)}

    for item in payload.series:
        rows = sorted(item.dailyDemand, key=lambda row: row.date)
        if len({row.date for row in rows}) != len(rows):
            raise HTTPException(status_code=422, detail=f"Duplicate dates for stock {item.stockId}")
        if len(rows) < 29:
            continue
        # Do not silently fill missing calendar dates: Java sends a complete daily series.
        if any((rows[i].date - rows[i - 1].date).days != 1 for i in range(1, len(rows))):
            raise HTTPException(status_code=422, detail=f"Daily history for stock {item.stockId} has date gaps")
        series.append({
            "stock_id": item.stockId,
            "facility_code": facility_map[item.facilityId],
            "group_code": group_map[item.bloodGroup],
            "dates": [row.date for row in rows],
            "values": [float(row.unitsIssued) for row in rows],
        })
    return series, len(facility_ids), len(groups)


@app.get("/health")
def health():
    return {"status": "UP", "model": MODEL_VERSION}


@app.post("/forecast")
def forecast(payload: ForecastRequest, x_api_key: str | None = Header(default=None)):
    expected_key = os.getenv("ML_API_KEY", "")
    if expected_key and not hmac.compare_digest(x_api_key or "", expected_key):
        raise HTTPException(status_code=401, detail="Invalid ML service API key")

    series, facility_count, group_count = _prepare(payload)
    if not series:
        return {"status": "INSUFFICIENT_DATA", "modelVersion": MODEL_VERSION,
                "message": "No stock series contains enough daily history.", "forecasts": []}

    latest_date = max(s["dates"][-1] for s in series)
    cutoff = latest_date - timedelta(days=HOLDOUT_DAYS)
    train_x, train_y, valid_x, valid_y, baseline_y = [], [], [], [], []

    for item in series:
        values = item["values"]
        for index in range(28, len(values)):
            day = item["dates"][index]
            features = _features(values[:index], day, item["facility_code"],
                                 item["group_code"], facility_count, group_count)
            target = values[index]
            if day <= cutoff:
                train_x.append(features)
                train_y.append(target)
            else:
                valid_x.append(features)
                valid_y.append(target)
                baseline_y.append(float(np.mean(values[max(0, index - 28):index])))

    active_days = sum(1 for value in train_y if value > 0)
    if len(train_x) < MIN_TRAIN_ROWS or active_days < MIN_ACTIVE_DAYS or len(valid_x) < 20:
        return {"status": "INSUFFICIENT_DATA", "modelVersion": MODEL_VERSION,
                "message": "XGBoost requires at least 120 training rows, 20 issue-days and 20 chronological validation rows.",
                "forecasts": []}

    model = xgb.XGBRegressor(
        objective="reg:squarederror",
        eval_metric="mae",
        n_estimators=350,
        max_depth=4,
        learning_rate=0.04,
        subsample=0.9,
        colsample_bytree=0.9,
        reg_lambda=2.0,
        min_child_weight=2,
        n_jobs=2,
        random_state=42,
    )
    model.fit(np.asarray(train_x, dtype=np.float32), np.asarray(train_y, dtype=np.float32))
    validation_predictions = np.maximum(
        0, model.predict(np.asarray(valid_x, dtype=np.float32))
    )
    xgb_mae = float(np.mean(np.abs(validation_predictions - np.asarray(valid_y))))
    baseline_mae = float(np.mean(np.abs(np.asarray(baseline_y) - np.asarray(valid_y))))

    # Never replace the transparent baseline unless XGBoost wins on a time-ordered holdout.
    if not (xgb_mae < baseline_mae * 0.98):
        return {"status": "BASELINE_WINS", "modelVersion": MODEL_VERSION,
                "validationMae": round(xgb_mae, 4), "baselineMae": round(baseline_mae, 4),
                "message": "XGBoost did not beat the 28-day moving-average baseline on the holdout window.",
                "forecasts": []}

    all_x, all_y = [], []
    for item in series:
        values = item["values"]
        for index in range(28, len(values)):
            all_x.append(_features(values[:index], item["dates"][index],
                                   item["facility_code"], item["group_code"],
                                   facility_count, group_count))
            all_y.append(values[index])
    model.fit(np.asarray(all_x, dtype=np.float32), np.asarray(all_y, dtype=np.float32))

    results = []
    for item in series:
        values = list(item["values"])
        next_day = item["dates"][-1] + timedelta(days=1)
        predictions = []
        for offset in range(14):
            day = next_day + timedelta(days=offset)
            features = _features(values, day, item["facility_code"],
                                 item["group_code"], facility_count, group_count)
            prediction = max(0.0, float(model.predict(
                np.asarray([features], dtype=np.float32)
            )[0]))
            predictions.append(prediction)
            values.append(prediction)
        results.append({
            "stockId": item["stock_id"],
            "predictedDemand7Days": int(math.ceil(sum(predictions[:7]))),
            "predictedDemand14Days": int(math.ceil(sum(predictions))),
        })

    return {"status": "TRAINED", "modelVersion": MODEL_VERSION,
            "validationMae": round(xgb_mae, 4), "baselineMae": round(baseline_mae, 4),
            "message": "XGBoost passed chronological holdout comparison against the moving-average baseline.",
            "forecasts": results}
