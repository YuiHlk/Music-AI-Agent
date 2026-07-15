"""Expose health and bounded WAV-analysis endpoints through FastAPI."""

from fastapi import FastAPI, File, HTTPException, UploadFile

from .analysis import analyze_wav

app = FastAPI(title="Music AI Audio Analysis", version="0.1.0")
MAX_UPLOAD_BYTES = 25 * 1024 * 1024


@app.get("/health")
def health() -> dict[str, str]:
    """Return the service liveness status."""
    return {"status": "ok"}


@app.post("/api/audio/analyze")
async def analyze_audio(file: UploadFile = File(...)) -> dict[str, int | float | str | None]:
    """Analyze a bounded WAV upload and return its filename and audio measurements.

    The endpoint returns HTTP 415 for an unsupported upload hint, HTTP 413 for
    content above 25 MiB, and HTTP 422 when the bytes are not supported PCM WAV.
    """
    filename = file.filename or ""
    # 部分客户端不能可靠填写 WAV MIME，因此扩展名可通过初筛；实际格式仍由解析器验证。
    if file.content_type not in {"audio/wav", "audio/x-wav", "audio/wave"} and not filename.lower().endswith(".wav"):
        raise HTTPException(status_code=415, detail="Only WAV uploads are supported")
    # 只多读取一个字节即可判定超限，避免为检查大小而无界读取上传内容。
    content = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(content) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="WAV file exceeds the 25 MiB limit")
    try:
        result = analyze_wav(content).to_dict()
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return {"filename": filename, **result}
