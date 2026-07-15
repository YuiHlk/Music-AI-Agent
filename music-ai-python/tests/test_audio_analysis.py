"""Verify PCM WAV analysis and its FastAPI transport boundary."""

import ast
import io
import math
import struct
import wave
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.analysis import analyze_wav
from app.main import app


def wav_bytes(seconds: float = 1.0, sample_rate: int = 8000) -> bytes:
    """Build a mono 16-bit PCM WAV containing a half-scale 440 Hz sine wave."""
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(sample_rate)
        # 半满幅正弦波的理论 peak 为 0.5，RMS 为 0.5 / sqrt(2)。
        samples = [int(0.5 * 32767 * math.sin(2 * math.pi * 440 * i / sample_rate))
                   for i in range(int(seconds * sample_rate))]
        output.writeframes(b"".join(struct.pack("<h", sample) for sample in samples))
    return buffer.getvalue()


def test_production_modules_have_docstrings():
    app_root = Path(__file__).parents[1] / "app"
    missing = []
    for path in sorted(app_root.glob("*.py")):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        if ast.get_docstring(tree) is None:
            missing.append(f"{path.name}:module")
        for node in ast.walk(tree):
            if isinstance(node, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)) and ast.get_docstring(node) is None:
                missing.append(f"{path.name}:{node.name}")
    assert missing == []


def test_analyze_pcm_wav_metadata_and_levels():
    result = analyze_wav(wav_bytes())
    assert result.sample_rate == 8000
    assert result.channels == 1
    assert result.sample_width_bits == 16
    assert result.duration_seconds == 1.0
    assert result.rms == pytest.approx(0.3535, abs=0.001)
    assert result.peak == pytest.approx(0.5, abs=0.001)


def test_rejects_invalid_wav():
    with pytest.raises(ValueError, match="valid WAV"):
        analyze_wav(b"not a wav")


def test_http_analysis_and_health():
    client = TestClient(app)
    assert client.get("/health").json() == {"status": "ok"}
    response = client.post("/api/audio/analyze", files={"file": ("riff.wav", wav_bytes(), "audio/wav")})
    assert response.status_code == 200
    assert response.json()["filename"] == "riff.wav"
    assert response.json()["sample_rate"] == 8000


def test_http_rejects_non_wav_upload():
    response = TestClient(app).post(
        "/api/audio/analyze", files={"file": ("notes.txt", b"text", "text/plain")}
    )
    assert response.status_code == 415
