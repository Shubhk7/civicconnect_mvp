"""
Before/after resolution verification.

Uses structural similarity (SSIM) between the citizen's original photo and
the officer's after-photo to estimate whether a real physical change
happened at the location — this is the C++/OpenCV-style verification step
from the architecture, implemented here in Python+OpenCV since SSIM doesn't
need a custom C++ pipeline to run fast enough for a demo. (If you want the
literal C++/OpenCV binary for the "why C++" story in your pitch, the same
SSIM approach ports directly — see note at bottom of this file.)
"""

import cv2
import numpy as np


def _to_gray_resized(image: np.ndarray, size=(400, 400)) -> np.ndarray:
    resized = cv2.resize(image, size)
    return cv2.cvtColor(resized, cv2.COLOR_BGR2GRAY)


def _ssim(img1: np.ndarray, img2: np.ndarray) -> float:
    """
    Lightweight SSIM implementation (no extra dependency on scikit-image).
    Returns a score from -1 to 1; 1 means identical images.
    """
    C1 = (0.01 * 255) ** 2
    C2 = (0.03 * 255) ** 2

    img1 = img1.astype(np.float64)
    img2 = img2.astype(np.float64)

    kernel = cv2.getGaussianKernel(11, 1.5)
    window = np.outer(kernel, kernel.transpose())

    mu1 = cv2.filter2D(img1, -1, window)[5:-5, 5:-5]
    mu2 = cv2.filter2D(img2, -1, window)[5:-5, 5:-5]
    mu1_sq, mu2_sq, mu1_mu2 = mu1 ** 2, mu2 ** 2, mu1 * mu2

    sigma1_sq = cv2.filter2D(img1 ** 2, -1, window)[5:-5, 5:-5] - mu1_sq
    sigma2_sq = cv2.filter2D(img2 ** 2, -1, window)[5:-5, 5:-5] - mu2_sq
    sigma12 = cv2.filter2D(img1 * img2, -1, window)[5:-5, 5:-5] - mu1_mu2

    ssim_map = ((2 * mu1_mu2 + C1) * (2 * sigma12 + C2)) / (
        (mu1_sq + mu2_sq + C1) * (sigma1_sq + sigma2_sq + C2)
    )
    return float(ssim_map.mean())


def compare_before_after(before: np.ndarray, after: np.ndarray) -> dict:
    before_gray = _to_gray_resized(before)
    after_gray = _to_gray_resized(after)

    similarity = _ssim(before_gray, after_gray)

    # Note: for a real resolution, we EXPECT some change (pothole filled,
    # garbage cleared) so a lower similarity score is actually the signal
    # that something changed. Too low, though, and it might just be a
    # completely unrelated photo — so we check a band, not a single
    # threshold.
    if similarity > 0.92:
        verdict = "no_change_detected"
        change_detected = False
    elif similarity < 0.25:
        verdict = "unreliable_possibly_unrelated_photo"
        change_detected = False
    else:
        verdict = "likely_resolved"
        change_detected = True

    return {
        "changeDetected": change_detected,
        "similarityScore": round(similarity, 3),
        "verdict": verdict,
    }


# --- Note on the C++ version for your architecture story ---
# The same SSIM logic above can be reimplemented in C++ using OpenCV's
# native cv::Mat operations (cv::GaussianBlur, cv::multiply, cv::divide)
# almost line-for-line. For a hackathon, running it here in Python is the
# pragmatic choice — you avoid a second language boundary/build step, and
# performance is not the bottleneck at demo scale (a handful of images, not
# a production photo pipeline). Position it in your pitch as: "verification
# uses OpenCV; a production version would move this hot path to C++ for
# throughput," which is both true and honest about what's actually running.
