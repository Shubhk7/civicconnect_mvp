FROM python:3.11-slim

WORKDIR /app

# OpenCV needs a couple system libs even in headless mode
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# Pre-download the base pretrained model at build time so the container
# doesn't hit the internet on first request.
RUN python -c "from ultralytics import YOLO; YOLO('yolov8n.pt')"

EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
