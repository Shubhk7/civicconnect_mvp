"""
CivicConnect AI service.

Two endpoints, matching the two AI touchpoints in the platform:

  POST /classify   -> given an uploaded photo, returns issue type + severity
                       (used right after a citizen submits a report)

  POST /verify      -> given a before-photo and after-photo, returns whether
                       a real change was detected (used after an officer
                       marks a complaint resolved)

Run standalone:
    uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload

The Spring Boot backend calls this service over HTTP — it does not need to
run in the same process or even the same machine.
"""

import io
import logging

import cv2
import numpy as np
from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from PIL import Image

from app.classify import IssueClassifier
from app.verify import compare_before_after
from app.blur import blur_faces_and_plates

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("civicconnect-ai")

app = FastAPI(title="CivicConnect AI Service", version="0.1.0")

# Allow the Spring Boot backend (and local dev tools) to call this freely.
# Tighten this to your actual backend's origin/IP before a real deployment.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

classifier = IssueClassifier()


def _read_image(file_bytes: bytes) -> np.ndarray:
    """Decode uploaded bytes into an OpenCV BGR image."""
    image = Image.open(io.BytesIO(file_bytes)).convert("RGB")
    return cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)


@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": classifier.is_loaded()}


@app.post("/classify")
async def classify(file: UploadFile = File(...)):
    """
    Returns something like:
    {
      "issueType": "pothole",
      "confidence": 0.87,
      "severity": "medium",
      "allDetections": [...]
    }
    """
    contents = await file.read()
    image = _read_image(contents)
    result = classifier.classify(image)
    return result


@app.post("/verify")
async def verify(before: UploadFile = File(...), after: UploadFile = File(...)):
    """
    Compares a before/after photo pair for the same complaint and returns
    whether a meaningful visual change was detected — this is the signal
    used to accept or reject an officer's "resolved" claim.

    Returns something like:
    {
      "changeDetected": true,
      "similarityScore": 0.62,
      "verdict": "likely_resolved"
    }
    """
    before_bytes = await before.read()
    after_bytes = await after.read()
    before_img = _read_image(before_bytes)
    after_img = _read_image(after_bytes)
    return compare_before_after(before_img, after_img)


@app.post("/blur")
async def blur(file: UploadFile = File(...)):
    """
    Detects and blurs faces and license plates in the uploaded photo, then
    returns the processed image bytes directly (image/jpeg). This is
    meant to run BEFORE a photo is ever stored or shown publicly — the
    Spring Boot backend's upload endpoint calls this first and only
    persists the returned (blurred) bytes, never the original upload.

    Detection counts are returned as response headers (not a JSON body,
    since the response body is the image itself) so the caller can log or
    surface how many regions were found:
      X-Faces-Blurred, X-Plates-Blurred, X-Blur-Note
    """
    contents = await file.read()
    image = _read_image(contents)
    summary = blur_faces_and_plates(image)

    success, encoded = cv2.imencode(".jpg", image)
    if not success:
        return Response(status_code=500, content=b"Failed to encode processed image")

    return Response(
        content=encoded.tobytes(),
        media_type="image/jpeg",
        headers={
            "X-Faces-Blurred": str(summary["facesBlurred"]),
            "X-Plates-Blurred": str(summary["platesBlurred"]),
            "X-Blur-Note": summary["note"],
        },
    )
