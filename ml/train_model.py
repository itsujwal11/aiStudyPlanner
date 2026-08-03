"""Train AASA's leakage-safe next-answer prediction model."""

import json
from pathlib import Path

import joblib
import matplotlib
import numpy as np
import pandas as pd
import seaborn as sns
from matplotlib import pyplot as plt
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (accuracy_score, confusion_matrix, f1_score,
                             precision_score, recall_score, roc_auc_score)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

BASE = Path(__file__).resolve().parent
DATA = BASE / "data" / "skill_builder_data.csv"
MODEL_DIR = BASE / "models"
REPORT_DIR = BASE / "reports"
SEED = 42
SOURCE = ["user_id", "skill_id", "order_id", "correct", "attempt_count",
          "ms_first_response", "opportunity"]
FEATURES = ["previous_attempts", "previous_accuracy",
            "average_response_time", "recent_accuracy", "opportunity"]


def prepare_data():
    if not DATA.is_file():
        raise FileNotFoundError(f"Dataset not found: {DATA}")
    print(f"Loading {DATA}")
    # The published CSV contains a small number of legacy Latin-1 characters.
    data = pd.read_csv(DATA, usecols=SOURCE, low_memory=False, encoding="latin-1")
    raw_rows = len(data)
    for column in SOURCE:
        data[column] = pd.to_numeric(data[column], errors="coerce")
    data = data.dropna(subset=["user_id", "skill_id", "order_id",
                               "correct", "opportunity"])
    data = data[data.correct.isin([0, 1]) & (data.opportunity >= 0)]
    data = data.drop_duplicates(subset=["order_id"], keep="first")
    data.loc[(data.ms_first_response < 0) |
             (data.ms_first_response > 3_600_000), "ms_first_response"] = np.nan
    data["response_seconds"] = data.ms_first_response / 1000.0
    data = data.sort_values(["user_id", "skill_id", "order_id"],
                            kind="stable").reset_index(drop=True)

    grouped = data.groupby(["user_id", "skill_id"], sort=False)
    keys = [data.user_id, data.skill_id]
    data["previous_attempts"] = grouped.cumcount().astype(float)
    previous_correct = grouped.correct.cumsum() - data.correct
    data["previous_accuracy"] = np.where(
        data.previous_attempts > 0,
        previous_correct / data.previous_attempts,
        0.5,
    )
    response = data.response_seconds
    response_sum = response.fillna(0).groupby(keys, sort=False).cumsum()
    response_count = response.notna().astype(int).groupby(keys, sort=False).cumsum()
    prior_sum = response_sum - response.fillna(0)
    prior_count = response_count - response.notna().astype(int)
    data["average_response_time"] = np.where(
        prior_count > 0, prior_sum / prior_count, np.nan)
    # shift(1) prevents the current target from entering current features.
    data["recent_accuracy"] = grouped.correct.transform(
        lambda values: values.shift(1).rolling(3, min_periods=1).mean()
    ).fillna(0.5)

    prepared = data[["user_id", *FEATURES, "correct"]].copy()
    prepared.correct = prepared.correct.astype(int)
    summary = {"source_rows": raw_rows, "usable_rows": len(prepared),
               "removed_rows": raw_rows - len(prepared),
               "students": prepared.user_id.nunique(),
               "original_columns": 30, "features": FEATURES,
               "target": "correct",
               "class_counts": {str(k): int(v) for k, v in
                                prepared.correct.value_counts().sort_index().items()}}
    print(f"Prepared {len(prepared):,} samples from "
          f"{prepared.user_id.nunique():,} students")
    return prepared, summary


def split_students(data):
    students = data.user_id.drop_duplicates().to_numpy(copy=True)
    np.random.default_rng(SEED).shuffle(students)
    train_end = int(len(students) * 0.70)
    valid_end = train_end + int(len(students) * 0.15)
    groups = {"train": set(students[:train_end]),
              "validation": set(students[train_end:valid_end]),
              "test": set(students[valid_end:])}
    splits = {name: data[data.user_id.isin(ids)].copy()
              for name, ids in groups.items()}
    for name, part in splits.items():
        print(f"{name}: {len(part):,} rows, {part.user_id.nunique():,} students")
    return splits


def pipeline(classifier, scale=False):
    steps = [("imputer", SimpleImputer(strategy="median"))]
    if scale:
        steps.append(("scaler", StandardScaler()))
    preprocessor = ColumnTransformer(
        [("numeric", Pipeline(steps), FEATURES)], remainder="drop")
    return Pipeline([("preprocessor", preprocessor),
                     ("classifier", classifier)])


def create_models():
    return {
        "logistic_regression": pipeline(LogisticRegression(
            max_iter=1000, class_weight="balanced", random_state=SEED), True),
        "random_forest": pipeline(RandomForestClassifier(
            n_estimators=200, max_depth=12, min_samples_leaf=20,
            class_weight="balanced", random_state=SEED, n_jobs=-1)),
    }


def measure(model, data):
    actual = data.correct
    predicted = model.predict(data[FEATURES])
    probability = model.predict_proba(data[FEATURES])[:, 1]
    matrix = confusion_matrix(actual, predicted, labels=[0, 1])
    return {"accuracy": float(accuracy_score(actual, predicted)),
            "precision": float(precision_score(actual, predicted, zero_division=0)),
            "recall": float(recall_score(actual, predicted, zero_division=0)),
            "f1_score": float(f1_score(actual, predicted, zero_division=0)),
            "roc_auc": float(roc_auc_score(actual, probability)),
            "confusion_matrix": matrix.tolist()}, matrix


def main():
    MODEL_DIR.mkdir(exist_ok=True)
    REPORT_DIR.mkdir(exist_ok=True)
    data, dataset_summary = prepare_data()
    splits = split_students(data)
    models = create_models()
    validation = {}
    for name, model in models.items():
        print(f"Training {name}...")
        model.fit(splits["train"][FEATURES], splits["train"].correct)
        validation[name], _ = measure(model, splits["validation"])
        result = validation[name]
        print(f"  F1={result['f1_score']:.4f}, "
              f"ROC-AUC={result['roc_auc']:.4f}")

    selected = max(validation, key=lambda name:
                   (validation[name]["f1_score"], validation[name]["roc_auc"]))
    model = models[selected]
    combined = pd.concat([splits["train"], splits["validation"]])
    model.fit(combined[FEATURES], combined.correct)
    test_metrics, matrix = measure(model, splits["test"])
    joblib.dump(model, MODEL_DIR / "weakness_model.joblib")

    split_report = {name: {"samples": len(part),
                           "students": part.user_id.nunique()}
                    for name, part in splits.items()}
    report = {"dataset": dataset_summary, "split": split_report,
              "models": {name: {"validation": result}
                         for name, result in validation.items()},
              "random_forest_parameters": {"n_estimators": 200,
                  "max_depth": 12, "min_samples_leaf": 20,
                  "class_weight": "balanced", "random_state": SEED},
              "selected_model": selected, "test": test_metrics,
              "weakness_formula": "1 - probability_correct",
              "hybrid_formula": "0.70 * evidence + 0.30 * ml_weakness"}
    (REPORT_DIR / "metrics.json").write_text(
        json.dumps(report, indent=2), encoding="utf-8")

    matplotlib.use("Agg")
    sns.heatmap(matrix, annot=True, fmt="d", cmap="Blues",
                xticklabels=["Incorrect", "Correct"],
                yticklabels=["Incorrect", "Correct"])
    plt.title(f"Test Confusion Matrix - {selected.replace('_', ' ').title()}")
    plt.xlabel("Predicted")
    plt.ylabel("Actual")
    plt.tight_layout()
    plt.savefig(REPORT_DIR / "confusion_matrix.png", dpi=180)
    plt.close()

    print(f"Selected: {selected}")
    print(json.dumps(test_metrics, indent=2))
    print(f"Model: {MODEL_DIR / 'weakness_model.joblib'}")
    print(f"Report: {REPORT_DIR / 'metrics.json'}")


if __name__ == "__main__":
    main()
