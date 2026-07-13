from fastapi import FastAPI, File, HTTPException, UploadFile

from .analysis import analyze_wav

app = FastAPI(title="Music AI Audio Analysis", version="0.1.0")
MAX_UPLOAD_BYTES = 25 * 1024 * 1024


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/audio/analyze")
async def analyze_audio(file: UploadFile = File(...)) -> dict[str, int | float | str | None]:
    filename = file.filename or ""
    if file.content_type not in {"audio/wav", "audio/x-wav", "audio/wave"} and not filename.lower().endswith(".wav"):
        raise HTTPException(status_code=415, detail="Only WAV uploads are supported")
    content = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(content) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="WAV file exceeds the 25 MiB limit")
    try:
        result = analyze_wav(content).to_dict()
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return {"filename": filename, **result}
