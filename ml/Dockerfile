FROM python:3.11-slim

WORKDIR /app

# Inference deps only - matplotlib/seaborn are training-time concerns.
COPY requirements-serve.txt .
RUN pip install --no-cache-dir -r requirements-serve.txt

COPY serve.py .

# The trained artifact is NOT baked into the image: it is large and gitignored.
# docker-compose mounts ./ml/models here. Regenerate with `python train_model.py`.
ENV MODEL_PATH=/app/models/weakness_model.joblib

EXPOSE 8000

CMD ["uvicorn", "serve:app", "--host", "0.0.0.0", "--port", "8000"]
