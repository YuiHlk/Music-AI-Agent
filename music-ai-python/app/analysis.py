from __future__ import annotations

import io
import math
import wave
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class AudioAnalysis:
    sample_rate: int
    channels: int
    sample_width_bits: int
    frame_count: int
    duration_seconds: float
    rms: float
    peak: float
    estimated_bpm: float | None

    def to_dict(self) -> dict[str, int | float | None]:
        return asdict(self)


def analyze_wav(content: bytes) -> AudioAnalysis:
    """Analyze an uncompressed PCM WAV without optional native dependencies."""
    try:
        with wave.open(io.BytesIO(content), "rb") as wav:
            channels = wav.getnchannels()
            sample_width = wav.getsampwidth()
            sample_rate = wav.getframerate()
            frame_count = wav.getnframes()
            compression = wav.getcomptype()
            raw = wav.readframes(frame_count)
    except (wave.Error, EOFError) as exc:
        raise ValueError("The uploaded file is not a valid WAV file") from exc

    if compression != "NONE" or sample_width not in (1, 2, 3, 4):
        raise ValueError("Only uncompressed 8/16/24/32-bit PCM WAV files are supported")
    if channels < 1 or sample_rate < 1 or frame_count < 1:
        raise ValueError("The WAV file does not contain audio frames")

    samples = _decode_pcm(raw, sample_width)
    mono = [sum(samples[i:i + channels]) / channels for i in range(0, len(samples), channels)]
    rms = math.sqrt(sum(value * value for value in mono) / len(mono))
    peak = max(abs(value) for value in mono)
    return AudioAnalysis(
        sample_rate=sample_rate,
        channels=channels,
        sample_width_bits=sample_width * 8,
        frame_count=frame_count,
        duration_seconds=round(frame_count / sample_rate, 6),
        rms=round(rms, 6),
        peak=round(peak, 6),
        estimated_bpm=_estimate_bpm(mono, sample_rate),
    )


def _decode_pcm(raw: bytes, width: int) -> list[float]:
    maximum = float(1 << (width * 8 - 1))
    result: list[float] = []
    for offset in range(0, len(raw), width):
        chunk = raw[offset:offset + width]
        if len(chunk) != width:
            break
        if width == 1:
            value = chunk[0] - 128
        else:
            value = int.from_bytes(chunk, "little", signed=True)
        result.append(value / maximum)
    return result


def _estimate_bpm(samples: list[float], sample_rate: int) -> float | None:
    # Build a 100 Hz amplitude envelope, then find the strongest tempo lag.
    hop = max(1, sample_rate // 100)
    envelope = [sum(abs(x) for x in samples[i:i + hop]) / hop
                for i in range(0, len(samples), hop)]
    if len(envelope) < 200 or max(envelope, default=0.0) < 1e-5:
        return None
    mean = sum(envelope) / len(envelope)
    centered = [x - mean for x in envelope]
    best_lag = None
    best_score = 0.0
    for lag in range(33, min(151, len(centered) // 2)):  # 40..182 BPM
        score = sum(centered[i] * centered[i - lag] for i in range(lag, len(centered)))
        if score > best_score:
            best_lag, best_score = lag, score
    return round(6000 / best_lag, 1) if best_lag and best_score > 0 else None
