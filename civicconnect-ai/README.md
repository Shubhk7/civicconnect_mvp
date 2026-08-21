# CivicConnect AI Service

FastAPI service wrapping YOLOv8 (classification) and OpenCV SSIM
(before/after verification). Called by the Spring Boot backend over HTTP —
keeps the AI/Python stack fully separate from the Java stack, as planned.

## Run with Docker (recommended)

```bash
docker build -t civicconnect-ai .
docker run -p 8001:8001 civicconnect-ai
```

Or add it to the backend's `docker-compose.yml` as its own service (see
`docker-compose.snippet.yml` in this folder — copy that block into the
backend's compose file to run everything together).

## Run locally without Docker

```bash
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

First run downloads the pretrained `yolov8n.pt` (~6MB) automatically.

## Test it

```bash
curl http://localhost:8001/health

curl -X POST http://localhost:8001/classify \
  -F "file=@/path/to/some_photo.jpg"

curl -X POST http://localhost:8001/verify \
  -F "before=@/path/to/before.jpg" \
  -F "after=@/path/to/after.jpg"
```

## Using your own fine-tuned model

Once you've fine-tuned YOLOv8 on a pothole/garbage dataset (see the main
project README for the Roboflow + training steps):

1. Drop your trained weights file (e.g. `best.pt`) into `models/`
2. Set the env var when running:
   ```bash
   docker run -p 8001:8001 -e CLASSIFIER_MODEL_PATH=models/best.pt civicconnect-ai
   ```
3. Update `CLASS_NAME_MAP` in `app/classify.py` to match your model's actual
   trained class names — check them with:
   ```python
   from ultralytics import YOLO
   m = YOLO("models/best.pt")
   print(m.names)
   ```

Until you do this, the service runs on the plain pretrained COCO model as a
placeholder — it'll detect generic objects, not civic issues specifically,
but the endpoint and response shape are already correct for the backend to
integrate against immediately.

## Wiring this into the Spring Boot backend

The backend doesn't call this yet — that's the next integration step. In
`ComplaintService.java`, after receiving a photo upload, add an HTTP call to
`http://<ai-service-host>:8001/classify` before running jurisdiction
routing, and use the returned `issueType` instead of (or to validate) the
citizen-selected one.
