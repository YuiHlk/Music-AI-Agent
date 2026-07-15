"""Analyze uncompressed PCM WAV audio with lightweight standard-library algorithms."""

from __future__ import annotations

import io
import math
import wave
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class AudioAnalysis:
    """Store immutable WAV metadata and normalized mono signal measurements.

    Attributes:
        sample_rate: Source sample rate in hertz.
        channels: Number of channels in the source WAV.
        sample_width_bits: PCM sample width in bits.
        frame_count: Number of source WAV frames before channel downmixing.
        duration_seconds: Audio duration in seconds.
        rms: Root mean square of the normalized mono signal.
        peak: Maximum absolute amplitude of the normalized mono signal.
        estimated_bpm: Approximate tempo, or ``None`` when no stable estimate exists.
    """

    sample_rate: int
    channels: int
    sample_width_bits: int
    frame_count: int
    duration_seconds: float
    rms: float
    peak: float
    estimated_bpm: float | None

    def to_dict(self) -> dict[str, int | float | None]:
        """Return the analysis fields as a JSON-compatible dictionary."""
        return asdict(self)


def analyze_wav(content: bytes) -> AudioAnalysis:
    """Analyze an uncompressed PCM WAV without optional native dependencies.

    Args:
        content: Complete WAV file contents.

    Returns:
        Source metadata plus normalized mono RMS, peak, and approximate tempo.

    Raises:
        ValueError: If the content is not a non-empty 8/16/24/32-bit PCM WAV.
    """
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
    # 所有响度和节拍指标统一基于声道算术平均；该轻量下混允许相位抵消，是当前有意的简化。
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
    """Decode little-endian PCM samples and normalize them to approximately [-1, 1]."""
    # 以负侧满量程 2^(bits-1) 归一化；有符号 PCM 的正峰值因此会略小于 1。
    maximum = float(1 << (width * 8 - 1))
    result: list[float] = []
    for offset in range(0, len(raw), width):
        chunk = raw[offset:offset + width]
        if len(chunk) != width:
            break
        # WAV 的 8-bit PCM 是无符号数据，较高位宽则使用小端有符号整数。
        if width == 1:
            value = chunk[0] - 128
        else:
            value = int.from_bytes(chunk, "little", signed=True)
        result.append(value / maximum)
    return result


def _estimate_bpm(samples: list[float], sample_rate: int) -> float | None:
    """Estimate tempo from amplitude-envelope autocorrelation; this is not beat transcription."""
    # 约 100 Hz 的包络足以覆盖节拍变化，并显著降低直接在音频采样率上自相关的计算量；
    # 整数 hop 在采样率不能被 100 整除时会带来轻微的 BPM 近似误差。
    hop = max(1, sample_rate // 100)
    envelope = [sum(abs(x) for x in samples[i:i + hop]) / hop
                for i in range(0, len(samples), hop)]
    # 约两秒以下的片段不足以稳定比较节拍周期，近静音信号也没有可用的响度包络。
    if len(envelope) < 200 or max(envelope, default=0.0) < 1e-5:
        return None
    # 去除平均响度，使自相关主要衡量包络的周期性变化。
    mean = sum(envelope) / len(envelope)
    centered = [x - mean for x in envelope]
    best_lag = None
    best_score = 0.0
    # lag=33..150 对应约 182..40 BPM，限制搜索范围可减少倍速/半速误判。
    for lag in range(33, min(151, len(centered) // 2)):
        score = sum(centered[i] * centered[i - lag] for i in range(lag, len(centered)))
        if score > best_score:
            best_lag, best_score = lag, score
    # 6000 = 每分钟 60 秒 × 每秒约 100 个包络样本，除以周期 lag 得到 BPM。
    return round(6000 / best_lag, 1) if best_lag and best_score > 0 else None
