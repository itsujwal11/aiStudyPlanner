"""AASA weakness-model inference service.

Serves the Random Forest trained by ``train_model.py`` to the Spring Boot
backend over HTTP. The backend blends this model's output with its own
evidence formula:

    weakness    = 1 - P(correct on next attempt)
    finalScore  = 0.70 * evidenceWeakness + 0.30 * modelWeakness

The service is deliberately fail-soft: if the joblib artifact is missing it
still starts and reports ``modelLoaded: false`` so the backend can degrade to
evidence-only scoring instead of failing requests. Regenerate the artifact with
``python train_model.py``.

Run locally:
    uvicorn serve:app --host 0.0.0.0 --port 8000
"""

import logging
import os
from pathlib import Path
from typing import List, Optional

import joblib
import pandas as pd
from fastapi import FastAPI
from pydantic import BaseModel, Field

# Must match FEATURES in train_model.py, in the same order the fitted
# ColumnTransformer expects.
FEATURES = ["previous_attempts", "previous_accuracy",
            "average_response_time", "recent_accuracy", "opportunity"]

BASE = Path(__file__).resolve().parent
MODEL_PATH = Path(os.environ.get("MODEL_PATH", BASE / "models" / "weakness_model.joblib"))

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("aasa.ml")

app = FastAPI(title="AASA Weakness Model", version="1.0.0")
_model = None


class Instance(BaseModel):
    """One (learner, topic) pair, described by its attempt history."""

    previous_attempts: float = Field(..., ge=0, description="Attempts on this topic so far")
    previous_accuracy: float = Field(..., ge=0, le=1, description="Correct ratio so far; 0.5 when no history")
    average_response_time: Optional[float] = Field(
        None, ge=0, description="Mean seconds per prior attempt; null when unknown (median-imputed)")
    recent_accuracy: float = Field(..., ge=0, le=1, description="Correct ratio over the last 3 attempts")
    opportunity: float = Field(..., ge=0, description="1-based index of the attempt being predicted")


class PredictRequest(BaseModel):
    instances: List[Instance] = Field(..., min_length=1, max_length=500)


class Prediction(BaseModel):
    probability_correct: float
    weakness: float


class PredictResponse(BaseModel):
    predictions: List[Prediction]
    model_version: str


@app.on_event("startup")
def load_model() -> None:
    global _model
    if not MODEL_PATH.is_file():
        logger.warning(
            "Model artifact not found at %s - /predict will return 503 and the "
            "backend will fall back to evidence-only weakness. Run "
            "`python train_model.py` to generate it.", MODEL_PATH)
        return
    _model = joblib.load(MODEL_PATH)
    logger.info("Loaded weakness model from %s", MODEL_PATH)


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "modelLoaded": _model is not None,
        "modelPath": str(MODEL_PATH),
        "features": FEATURES,
    }


@app.post("/predict", response_model=PredictResponse, responses={503: {"description": "Model unavailable"}})
def predict(request: PredictRequest):
    if _model is None:
        # 503 is the agreed signal for "degrade to evidence-only", not an error
        # the caller should retry or surface to the student.
        from fastapi.responses import JSONResponse
        return JSONResponse(status_code=503, content={"detail": "weakness model not loaded"})

    frame = pd.DataFrame(
        [[getattr(instance, name) for name in FEATURES] for instance in request.instances],
        columns=FEATURES,
    )
    # average_response_time may be None -> NaN, which the pipeline's
    # SimpleImputer(strategy="median") fills using the training distribution.
    frame = frame.astype(float)

    probabilities = _model.predict_proba(frame)[:, 1]
    return PredictResponse(
        predictions=[
            Prediction(probability_correct=float(p), weakness=float(1.0 - p))
            for p in probabilities
        ],
        model_version="random_forest-1.0.0",
    )
