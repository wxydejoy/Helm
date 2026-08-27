"""Mini 上的守岸人 TTS。独立进程，不要 import dock_hub。

Hub 当客户端：POST /v1/speak、POST /v1/stop。安卓不直连本服务。

默认：Qwen3-TTS Base 6bit + 守岸人 ref_audio 克隆。
Serena / CustomVoice 用 --no-clone 和对应 --model 切回去。

--play：合成过程中在本机喇叭/耳机出声。Mac Mini 没有内置喇叭，
接了输出才听得到。play_only 时给 Hub 的是 202，不把 wav 回给手机。

同轮多次 /v1/speak（边写边念）接到同一条播放队列，不要互相 flush。
只有 /v1/stop 才清空缓冲并打断这一轮合成。
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
from mlx_audio.tts.audio_player import AudioPlayer
from mlx_audio.tts.utils import load_model

HERE = Path(__file__).resolve().parent
MODEL_ID = "mlx-community/Qwen3-TTS-12Hz-0.6B-Base-6bit"
CUSTOMVOICE_MODEL_ID = "mlx-community/Qwen3-TTS-12Hz-0.6B-CustomVoice-6bit"
DEFAULT_VOICE = "shorekeeper"
DEFAULT_LANG = "chinese"
DEFAULT_REF_AUDIO = HERE / "voices" / "shorekeeper" / "clone.wav"
MAX_BODY = 16_384
STREAM_INTERVAL = 0.4


class _LocalSpeaker(AudioPlayer):
    """尽快开口。同轮逐句接到缓冲里；只有 /v1/stop 才打断。"""

    min_buffer_seconds = 0.25

    def start_stream(self):
        import sounddevice as sd

        self.stream = sd.OutputStream(
            samplerate=self.sample_rate,
            channels=1,
            callback=self.callback,
            blocksize=self.buffer_size,
        )
        self.stream.start()
        self.playing = True
        self.drain_event.clear()

    def flush(self):
        with self.buffer_lock:
            self.audio_buffer.clear()
        self.stop_stream()


class Engine:
    def __init__(
        self,
        model_id: str,
        voice: str,
        ref_audio: str | None,
        ref_text: str | None,
        play: bool,
    ) -> None:
        self.model_id = model_id
        self.voice = voice
        self.ref_audio = ref_audio
        self.ref_text = ref_text
        self.play = play
        self.model = None
        self.error: str | None = None
        self.lock = threading.Lock()
        self._play_lock = threading.Lock()
        self._gate = threading.Lock()
        self._gen = 0
        self.speaker = _LocalSpeaker(sample_rate=24000) if play else None

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
                self._warmup()
                return
            speakers = []
            if hasattr(self.model, "get_supported_speakers"):
                speakers = list(self.model.get_supported_speakers() or [])
            print(f"就绪。音色：{', '.join(speakers) or self.voice}", flush=True)
        except Exception as exc:  # noqa: BLE001
            self.error = str(exc)
            print(f"加载失败：{exc}", flush=True)

    def _warmup(self) -> None:
        if self.model is None or not self.clone():
            return
        t0 = time.monotonic()
        list(
            self.model.generate(
                text="嗯。",
                lang_code=DEFAULT_LANG,
                verbose=False,
                stream=False,
                ref_audio=self.ref_audio,
                ref_text=self.ref_text,
            )
        )
        ms = max(0, int((time.monotonic() - t0) * 1000))
        print(f"克隆预热 ms={ms}", flush=True)

    def ready(self) -> bool:
        return self.model is not None and self.error is None

    def stop(self) -> None:
        with self._play_lock:
            with self._gate:
                self._gen += 1
            if self.speaker is not None:
                self.speaker.flush()

    def speak_token(self) -> int:
        with self._gate:
            return self._gen

    def speak(self, text: str, voice: str | None, language: str | None, expected_gen: int | None = None) -> tuple[bytes, int]:
        if self.model is None:
            raise RuntimeError(self.error or "模型还没就绪")
        spoken = text.strip()
        if not spoken:
            raise ValueError("text 不能为空")
        lang = (language or DEFAULT_LANG).strip() or DEFAULT_LANG
        use_stream = bool(self.play)
        kwargs: dict = {
            "text": spoken,
            "lang_code": lang,
            "verbose": False,
            "stream": use_stream,
        }
        if use_stream:
            kwargs["streaming_interval"] = STREAM_INTERVAL
        if self.clone():
            # ICL 要求 voice=None。Hub 仍可能发 Serena，这里丢掉。
            kwargs["ref_audio"] = self.ref_audio
            kwargs["ref_text"] = self.ref_text
        else:
            kwargs["voice"] = (voice or self.voice).strip() or self.voice
        chunks: list[np.ndarray] = []
        sample_rate = 24000
        t0 = time.monotonic()
        first_ms = 0
        with self.lock:
            with self._play_lock:
                with self._gate:
                    if expected_gen is not None and self._gen != expected_gen:
                        raise InterruptedError("stopped")
                    my = self._gen
            for result in self.model.generate(**kwargs):
                sample_rate = int(getattr(result, "sample_rate", sample_rate) or sample_rate)
                audio = np.asarray(result.audio, dtype=np.float32)
                if not chunks:
                    first_ms = max(0, int((time.monotonic() - t0) * 1000))
                chunks.append(audio)
                with self._play_lock:
                    with self._gate:
                        if self._gen != my:
                            if self.speaker is not None:
                                self.speaker.flush()
                            raise InterruptedError("stopped")
                    if self.speaker is not None:
                        self.speaker.queue_audio(audio)
        if not chunks:
            raise RuntimeError("模型没有发出声音")
        wav = np.concatenate(chunks, axis=0) if len(chunks) > 1 else chunks[0]
        buf = io.BytesIO()
        audio_write(buf, wav, sample_rate, format="wav")
        return buf.getvalue(), first_ms


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
                        "play": engine.play,
                        "ref_audio": engine.ref_audio,
                        "error": engine.error,
                    },
                )
                return
            self._json(404, {"error": {"code": "not_found", "message": "未知接口"}})

        def do_POST(self) -> None:  # noqa: N802
            path = urlparse(self.path).path.rstrip("/") or "/"
            try:
                body = self._read_json()
                if path == "/v1/stop":
                    engine.stop()
                    print("tts latency turn=- stage=stopped", flush=True)
                    self._json(200, {"ok": True, "stopped": True})
                    return
                if path != "/v1/speak":
                    self._json(404, {"error": {"code": "not_found", "message": "未知接口"}})
                    return
                if not engine.ready():
                    self._json(
                        503,
                        {"error": {"code": "not_ready", "message": engine.error or "模型加载中"}},
                    )
                    return
                text = str(body.get("text") or "")
                voice = body.get("voice")
                language = body.get("language")
                turn = str(body.get("turn_id") or "").strip() or "-"
                play_only = bool(body.get("play_only"))
                if play_only:
                    spoken = text.strip()
                    if not spoken:
                        raise ValueError("text 不能为空")
                    threading.Thread(
                        target=_play_only,
                        args=(engine, spoken, str(voice) if voice else None, str(language) if language else None, turn, engine.speak_token()),
                        name=f"tts-play-{turn}",
                        daemon=True,
                    ).start()
                    self._json(202, {"ok": True, "play": engine.play, "turn_id": turn})
                    return
                t0 = time.monotonic()
                wav, first_ms = engine.speak(
                    text,
                    str(voice) if voice else None,
                    str(language) if language else None,
                )
            except ValueError as exc:
                self._json(400, {"error": {"code": "bad_request", "message": str(exc)}})
                return
            except Exception as exc:  # noqa: BLE001
                self._json(502, {"error": {"code": "tts_error", "message": str(exc)}})
                return
            ms = max(0, int((time.monotonic() - t0) * 1000))
            print(
                f"tts latency turn={turn} stage=speak_done first_ms={first_ms} "
                f"ms={ms} bytes={len(wav)} chars={len(text.strip())} play={int(engine.play)}",
                flush=True,
            )
            self.send_response(200)
            self.send_header("Content-Type", "audio/wav")
            self.send_header("Content-Length", str(len(wav)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-First-Audio-Ms", str(first_ms))
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


def _play_only(
    engine: Engine,
    text: str,
    voice: str | None,
    language: str | None,
    turn: str,
    expected_gen: int,
) -> None:
    t0 = time.monotonic()
    try:
        wav, first_ms = engine.speak(text, voice, language, expected_gen=expected_gen)
    except InterruptedError:
        ms = max(0, int((time.monotonic() - t0) * 1000))
        print(f"tts latency turn={turn} stage=play_stopped ms={ms}", flush=True)
        return
    except Exception as exc:  # noqa: BLE001
        print(f"tts play_only turn={turn} error={exc}", flush=True)
        return
    ms = max(0, int((time.monotonic() - t0) * 1000))
    print(
        f"tts latency turn={turn} stage=play_only first_ms={first_ms} "
        f"ms={ms} bytes={len(wav)} chars={len(text)} play={int(engine.play)}",
        flush=True,
    )


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
    parser.add_argument(
        "--play",
        action="store_true",
        help="合成时在本机播放。Mini 需接耳机或外接音箱",
    )
    args = parser.parse_args()

    ref_audio, ref_text = _resolve_refs(args)
    engine = Engine(args.model, args.voice, ref_audio, ref_text, args.play)
    engine.load()
    if not engine.ready():
        raise SystemExit(1)

    server = ThreadingHTTPServer((args.host, args.port), make_handler(engine))
    extra = "  本机播放" if args.play else ""
    print(f"TTS 听 {args.host}:{args.port}  POST /v1/speak /v1/stop{extra}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("停止 TTS", flush=True)
        server.shutdown()


if __name__ == "__main__":
    main()
