"""Mini 上的守岸人 TTS。独立进程，不要 import dock_hub。

Hub 当客户端：POST /v1/speak  → audio/wav
安卓不直连本服务。

默认：Qwen3-TTS Base 6bit + 守岸人 ref_audio 克隆。
Serena / CustomVoice 用 --no-clone 和对应 --model 切回去。
"""

from __future__ import annotations

import argparse
import io
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

import numpy as np
from mlx_audio.audio_io import write as audio_write
from mlx_audio.tts.utils import load_model

HERE = Path(__file__).resolve().parent
MODEL_ID = "mlx-community/Qwen3-TTS-12Hz-0.6B-Base-6bit"
CUSTOMVOICE_MODEL_ID = "mlx-community/Qwen3-TTS-12Hz-0.6B-CustomVoice-6bit"
DEFAULT_VOICE = "shorekeeper"
DEFAULT_LANG = "chinese"
DEFAULT_REF_AUDIO = HERE / "voices" / "shorekeeper" / "default.wav"
MAX_BODY = 16_384


class Engine:
    def __init__(
        self,
        model_id: str,
        voice: str,
        ref_audio: str | None,
        ref_text: str | None,
    ) -> None:
        self.model_id = model_id
        self.voice = voice
        self.ref_audio = ref_audio
        self.ref_text = ref_text
        self.model = None
        self.error: str | None = None
        self.lock = threading.Lock()

    def clone(self) -> bool:
        return bool(self.ref_audio and self.ref_text)

    def load(self) -> None:
        try:
            print(f"加载 {self.model_id} …", flush=True)
            self.model = load_model(self.model_id)
            if self.clone():
                print(
                    f"就绪。克隆 {self.voice}  ref={self.ref_audio}",
                    flush=True,
                )
                return
            speakers = []
            if hasattr(self.model, "get_supported_speakers"):
                speakers = list(self.model.get_supported_speakers() or [])
            print(f"就绪。音色：{', '.join(speakers) or self.voice}", flush=True)
        except Exception as exc:  # noqa: BLE001
            self.error = str(exc)
            print(f"加载失败：{exc}", flush=True)

    def ready(self) -> bool:
        return self.model is not None and self.error is None

    def speak(self, text: str, voice: str | None, language: str | None) -> bytes:
        if self.model is None:
            raise RuntimeError(self.error or "模型还没就绪")
        spoken = text.strip()
        if not spoken:
            raise ValueError("text 不能为空")
        lang = (language or DEFAULT_LANG).strip() or DEFAULT_LANG
        kwargs: dict = {
            "text": spoken,
            "lang_code": lang,
            "verbose": False,
            "stream": False,
        }
        if self.clone():
            # ICL 要求 voice=None。Hub 仍可能发 Serena，这里丢掉。
            kwargs["ref_audio"] = self.ref_audio
            kwargs["ref_text"] = self.ref_text
        else:
            kwargs["voice"] = (voice or self.voice).strip() or self.voice
        chunks: list[np.ndarray] = []
        sample_rate = 24000
        with self.lock:
            for result in self.model.generate(**kwargs):
                sample_rate = int(getattr(result, "sample_rate", sample_rate) or sample_rate)
                audio = result.audio
                chunks.append(np.asarray(audio))
        if not chunks:
            raise RuntimeError("模型没有发出声音")
        wav = np.concatenate(chunks, axis=0) if len(chunks) > 1 else chunks[0]
        buf = io.BytesIO()
        audio_write(buf, wav, sample_rate, format="wav")
        return buf.getvalue()


def _ref_text_from_lab(audio_path: Path) -> str:
    lab = audio_path.with_suffix(".lab")
    if not lab.is_file():
        return ""
    return lab.read_text(encoding="utf-8").strip()


def _resolve_refs(args: argparse.Namespace) -> tuple[str | None, str | None]:
    if args.no_clone:
        return None, None
    raw = (args.ref_audio or "").strip()
    if not raw:
        return None, None
    audio = Path(raw).expanduser()
    if not audio.is_file():
        raise SystemExit(f"参考音频不存在：{audio}")
    text = (args.ref_text or "").strip() or _ref_text_from_lab(audio)
    if not text:
        raise SystemExit("克隆需要 --ref-text 或同目录 .lab")
    return str(audio), text


def make_handler(engine: Engine):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt: str, *args: object) -> None:
            print(f"tts {self.address_string()} {fmt % args}", flush=True)

        def do_GET(self) -> None:  # noqa: N802
            path = urlparse(self.path).path.rstrip("/") or "/"
            if path == "/health":
                self._json(
                    200,
                    {
                        "ok": True,
                        "service": "helm-companion-tts",
                        "ready": engine.ready(),
                        "model": engine.model_id,
                        "voice": engine.voice,
                        "clone": engine.clone(),
                        "ref_audio": engine.ref_audio,
                        "error": engine.error,
                    },
                )
                return
            self._json(404, {"error": {"code": "not_found", "message": "未知接口"}})

        def do_POST(self) -> None:  # noqa: N802
            path = urlparse(self.path).path.rstrip("/") or "/"
            if path != "/v1/speak":
                self._json(404, {"error": {"code": "not_found", "message": "未知接口"}})
                return
            if not engine.ready():
                self._json(
                    503,
                    {"error": {"code": "not_ready", "message": engine.error or "模型加载中"}},
                )
                return
            try:
                body = self._read_json()
                text = str(body.get("text") or "")
                voice = body.get("voice")
                language = body.get("language")
                turn = str(body.get("turn_id") or "").strip() or "-"
                t0 = time.monotonic()
                wav = engine.speak(text, str(voice) if voice else None, str(language) if language else None)
            except ValueError as exc:
                self._json(400, {"error": {"code": "bad_request", "message": str(exc)}})
                return
            except Exception as exc:  # noqa: BLE001
                self._json(502, {"error": {"code": "tts_error", "message": str(exc)}})
                return
            ms = max(0, int((time.monotonic() - t0) * 1000))
            print(
                f"tts latency turn={turn} stage=speak_done ms={ms} bytes={len(wav)} chars={len(text.strip())}",
                flush=True,
            )
            self.send_response(200)
            self.send_header("Content-Type", "audio/wav")
            self.send_header("Content-Length", str(len(wav)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(wav)

        def _read_json(self) -> dict:
            length_raw = self.headers.get("Content-Length", "0")
            try:
                length = int(length_raw)
            except ValueError as exc:
                raise ValueError("JSON 无效") from exc
            if length < 0 or length > MAX_BODY:
                raise ValueError("请求体过大")
            raw = self.rfile.read(length) if length else b"{}"
            if not raw:
                return {}
            data = json.loads(raw.decode("utf-8"))
            if not isinstance(data, dict):
                raise ValueError("JSON 必须是对象")
            return data

        def _json(self, status: int, payload: dict) -> None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

    return Handler


def main() -> None:
    parser = argparse.ArgumentParser(description="Helm companion TTS")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=18100)
    parser.add_argument("--model", default=MODEL_ID)
    parser.add_argument("--voice", default=DEFAULT_VOICE)
    parser.add_argument("--ref-audio", default=str(DEFAULT_REF_AUDIO))
    parser.add_argument(
        "--ref-text",
        default="",
        help="参考音频文案；空则读同名 .lab",
    )
    parser.add_argument(
        "--no-clone",
        action="store_true",
        help=f"关掉克隆。Serena 例：--no-clone --model {CUSTOMVOICE_MODEL_ID} --voice Serena",
    )
    args = parser.parse_args()

    ref_audio, ref_text = _resolve_refs(args)
    engine = Engine(args.model, args.voice, ref_audio, ref_text)
    engine.load()
    if not engine.ready():
        raise SystemExit(1)

    server = ThreadingHTTPServer((args.host, args.port), make_handler(engine))
    print(f"TTS 听 {args.host}:{args.port}  POST /v1/speak", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("停止 TTS", flush=True)
        server.shutdown()


if __name__ == "__main__":
    main()
