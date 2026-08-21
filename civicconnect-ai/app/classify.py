"""
Issue classification using a pretrained/fine-tuned YOLOv8 model.

For the hackathon MVP this ships with a plain pretrained YOLOv8n (trained on
COCO, general objects) as a fallback so the endpoint always returns
something. Once you fine-tune on a pothole/garbage dataset (see README),
point MODEL_PATH at your trained weights and detections will be your actual
civic-issue classes instead of generic COCO labels.
"""

import logging
import os

from ultralytics import YOLO

logger = logging.getLogger("civicconnect-ai")

# Swap this to your fine-tuned weights once you have them, e.g.:
#   MODEL_PATH = "models/civic_issues_yolov8n.pt"
MODEL_PATH = os.environ.get("CLASSIFIER_MODEL_PATH", "yolov8n.pt")

# Map your fine-tuned model's class names to the issue_type values the
# backend expects (pothole, garbage, streetlight, water_leak, ewaste).
# Adjust this once you know your trained model's actual class names —
# check `model.names` after loading your fine-tuned weights.
CLASS_NAME_MAP = {
    "pothole": "pothole",
    "garbage": "garbage",
    "trash": "garbage",
    "streetlight": "streetlight",
    "water": "water_leak",
    "e-waste": "ewaste",
    "ewaste": "ewaste",
}

# Confidence thresholds used to bucket a detection into a severity label.
# This is intentionally simple — a real severity model would look at size,
# location, and issue-specific rules, not just detection confidence.
def _severity_from_confidence(conf: float) -> str:
    if conf >= 0.75:
        return "high"
    if conf >= 0.45:
        return "medium"
    return "low"


class IssueClassifier:
    def __init__(self):
        self._model = None
        try:
            self._model = YOLO(MODEL_PATH)
            logger.info("Loaded classification model from %s", MODEL_PATH)
        except Exception as exc:  # noqa: BLE001
            logger.warning("Could not load model at %s: %s", MODEL_PATH, exc)

    def is_loaded(self) -> bool:
        return self._model is not None

    def classify(self, image) -> dict:
        if self._model is None:
            return {
                "issueType": "unknown",
                "confidence": 0.0,
                "severity": "unknown",
                "allDetections": [],
                "note": "Model not loaded — check CLASSIFIER_MODEL_PATH",
            }

        results = self._model(image, verbose=False)[0]

        detections = []
        for box in results.boxes:
            cls_id = int(box.cls[0])
            raw_name = self._model.names[cls_id]
            confidence = float(box.conf[0])
            detections.append({"label": raw_name, "confidence": round(confidence, 3)})

        if not detections:
            return {
                "issueType": "unclassified",
                "confidence": 0.0,
                "severity": "unknown",
                "allDetections": [],
                "note": "No objects detected — falls back to manual selection",
            }

        # Take the highest-confidence detection as the primary classification
        best = max(detections, key=lambda d: d["confidence"])
        issue_type = CLASS_NAME_MAP.get(best["label"].lower(), best["label"])

        return {
            "issueType": issue_type,
            "confidence": best["confidence"],
            "severity": _severity_from_confidence(best["confidence"]),
            "allDetections": detections,
        }
