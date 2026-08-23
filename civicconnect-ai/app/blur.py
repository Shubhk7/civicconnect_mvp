"""
Face and license-plate blurring, applied before any photo is stored or
shown publicly — per the platform's privacy design (Project Synopsis,
Section 10).

Faces: uses OpenCV's built-in Haar cascade face detector. This ships with
opencv-python (no separate model download needed), and is more than
adequate for a hackathon demo — it will miss some angled/small faces,
which is a real limitation worth stating honestly rather than overclaiming
"complete anonymization."

License plates: uses the same cascade family (haarcascade_russian_plate_number,
also bundled with OpenCV) as a first pass. Indian plate detection
specifically would benefit from a fine-tuned model, which is out of scope
for this pass — documented as a known limitation, not silently skipped.

Both detected regions are blurred with a strong Gaussian blur — not
pixelation, not a black box — since a strong blur is harder to reverse via
simple sharpening filters than pixelation is.
"""

import cv2
import numpy as np

_face_cascade = cv2.CascadeClassifier(
    cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
)
_plate_cascade = cv2.CascadeClassifier(
    cv2.data.haarcascades + "haarcascade_russian_plate_number.xml"
)


def _blur_region(image: np.ndarray, x: int, y: int, w: int, h: int) -> None:
    """Blurs a rectangular region of the image in place."""
    roi = image[y:y + h, x:x + w]
    if roi.size == 0:
        return
    # Kernel size must be odd and scales with the region so blur strength
    # stays proportionate on both small and large detections.
    k = max(15, (min(w, h) // 2) | 1)
    blurred = cv2.GaussianBlur(roi, (k, k), 0)
    image[y:y + h, x:x + w] = blurred


def blur_faces_and_plates(image: np.ndarray) -> dict:
    """
    Detects and blurs faces and license plates in place on the given BGR
    image. Returns a summary dict of what was found, so the API response
    can be honest about what was (and wasn't) detected rather than
    silently claiming full protection.
    """
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    faces = _face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30))
    for (x, y, w, h) in faces:
        _blur_region(image, x, y, w, h)

    plates = _plate_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(40, 15))
    for (x, y, w, h) in plates:
        _blur_region(image, x, y, w, h)

    return {
        "facesBlurred": int(len(faces)),
        "platesBlurred": int(len(plates)),
        "note": (
            "Detection is heuristic (Haar cascades), not guaranteed complete. "
            "Angled, small, or partially obscured faces/plates may be missed."
        ),
    }
