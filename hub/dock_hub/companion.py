from __future__ import annotations

import json
import re
import threading
import time
import uuid
import urllib.error
import urllib.request
from collections import OrderedDict
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlparse, urlunparse

from dock_hub.config import CompanionConfig, ID_RE
from dock_hub.errors import HubError

MAX_CLIPS = 8

DEFAULT_PERSONA = """你是守岸人，正在漂泊者的书桌上值班。说话慢、短、轻，偏诗意，不卖萌，不喊口号，不讲游戏剧情。
每次只回 1～3 句。可以叫对方「漂泊者」。不知道就说不知道，不要编造。
你会根据「当前状态」回答；状态里没有的，不要假装看见。
不要输出 ACTION 行，也不要声称已经开灯或开了程序。"""

_THINK_RE = re.compile(r"<think>.*?</think>", re.DOTALL | re.IGNORECASE)
_ACTION_RE = re.compile(r"^\s*ACTION:\s*\S+\s*$", re.MULTILINE)


@dataclass
class CompanionReply:
    text: str
    audio_id: str | None = None


class Companion:
    def __init__(self, config: CompanionConfig | None) -> None:
        self.config = config or CompanionConfig(enabled=False)
        self._ready = False
        self._voice = False
        self._ready_at = 0.0
        self._voice_at = 0.0
        self._speaking = False
        self._seq = 0
        self._lock = threading.Lock()
        self._clips: OrderedDict[str, bytes] = OrderedDict()

    def configure(self, config: CompanionConfig | None) -> None:
        self.config = config or CompanionConfig(enabled=False)
        self._ready = False
        self._voice = False
        self._ready_at = 0.0
        self._voice_at = 0.0
        with self._lock:
            self._speaking = False
            self._seq += 1
            self._clips.clear()

    def snapshot(self) -> dict[str, Any] | None:
        if not self.config.enabled:
            return None
        with self._lock:
            speaking = self._speaking
        return {
            "ready": self.ready(),
            "voice": self.voice(),
            "speaking": speaking,
        }

    def ready(self) -> bool:
        if not self.config.enabled:
            return False
        now = time.monotonic()
        if now - self._ready_at < 5:
            return self._ready
        self._ready = self._ping_llm()
        self._ready_at = now
        return self._ready

    def chat(self, body: dict[str, Any], facts: str) -> dict[str, Any]:
        if not self.config.enabled:
            raise HubError("not_found", "未开启桌面伴侣")
        if not isinstance(body, dict):
            raise HubError("bad_request", "JSON 必须是对象")
        text = str(body.get("text") or "").strip()
        if not text:
            raise HubError("bad_request", "text 不能为空")
        turn = _turn_id(body)
        started = time.monotonic()
        _latency(turn, "chat_recv", chars=len(text))
        with self._lock:
            self._seq += 1
            seq = self._seq
        self._stop_tts()
        persona = (self.config.persona or DEFAULT_PERSONA).strip()
        messages = [
            {"role": "system", "content": persona},
            {"role": "user", "content": f"当前状态：\n{facts}\n\n漂泊者：{text}"},
        ]
        llm_at = time.monotonic()
        try:
            raw = self._complete(messages)
        except HubError:
            _latency(turn, "ollama_error", ms=_ms(llm_at))
            raise
        spoken = _clean_reply(raw)
        if not spoken:
            _latency(turn, "ollama_empty", ms=_ms(llm_at))
            raise HubError("companion_unavailable", "大脑没有说出话")
        _latency(turn, "ollama_done", ms=_ms(llm_at), chars=len(spoken))
        self._ready = True
        self._ready_at = time.monotonic()
        with self._lock:
            if seq != self._seq:
                _latency(turn, "barge_in", ms=_ms(started))
                return {"text": spoken, "audio_id": None}
        audio_id = self._maybe_speak(spoken, turn)
        _latency(turn, "chat_total", ms=_ms(started), audio=1 if audio_id else 0)
        return {"text": spoken, "audio_id": audio_id}

    def stop(self) -> dict[str, Any]:
        if not self.config.enabled:
            raise HubError("not_found", "未开启桌面伴侣")
        with self._lock:
            self._seq += 1
            self._speaking = False
        self._stop_tts()
        _latency("-", "stop")
        return {"ok": True}

    def audio(self, ident: str) -> bytes:
        if not self.config.enabled:
            raise HubError("not_found", "未开启桌面伴侣")
        if not ident or not ID_RE.match(ident):
            raise HubError("not_found", "没有这段声音")
        with self._lock:
            wav = self._clips.get(ident)
        if not wav:
            raise HubError("not_found", "没有这段声音")
        _latency(ident, "audio_get", bytes=len(wav))
        return wav

    def voice(self) -> bool:
        if not self.config.enabled or not self.config.tts_base_url:
            return False
        now = time.monotonic()
        if now - self._voice_at < 5:
            return self._voice
        self._voice = self._ping_tts()
        self._voice_at = now
        return self._voice

    def _maybe_speak(self, spoken: str, turn: str) -> str | None:
        if not self.config.tts_base_url:
            return None
        with self._lock:
            self._speaking = True
        try:
            wav = self._speak(spoken, turn)
        finally:
            with self._lock:
                self._speaking = False
        if not self.config.tts_deliver or not wav:
            return None
        ident = turn
        with self._lock:
            self._clips[ident] = wav
            while len(self._clips) > MAX_CLIPS:
                self._clips.popitem(last=False)
        return ident

    def _stop_tts(self) -> None:
        if not self.config.tts_base_url:
            return
        url = _join(self.config.tts_base_url, "/v1/stop")
        req = urllib.request.Request(
            url,
            data=b"{}",
            method="POST",
            headers={"Content-Type": "application/json", "Accept": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, timeout=0.8) as resp:
                resp.read()
        except (TimeoutError, urllib.error.URLError, OSError):
            return

    def _ping_llm(self) -> bool:
        url = _join(self.config.llm_base_url, "/api/tags")
        try:
            req = urllib.request.Request(url, method="GET")
            with urllib.request.urlopen(req, timeout=0.4) as resp:
                return 200 <= resp.status < 300
        except (urllib.error.URLError, TimeoutError, OSError):
            return False

    def _complete(self, messages: list[dict[str, str]]) -> str:
        url = _join(self.config.llm_base_url, "/api/chat")
        payload = {
            "model": self.config.llm_model,
            "messages": messages,
            "stream": False,
            "think": False,
            "options": {
                "num_ctx": self.config.num_ctx,
                "num_predict": self.config.num_predict,
                "temperature": 0.7,
                "presence_penalty": 0.0,
            },
        }
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=data,
            method="POST",
            headers={"Content-Type": "application/json", "Accept": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, timeout=self.config.timeout_sec) as resp:
                raw = resp.read().decode("utf-8")
        except TimeoutError as exc:
            raise HubError("companion_unavailable", "大脑响应超时") from exc
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")[:200]
            raise HubError("companion_unavailable", f"大脑拒绝请求：{exc.code} {detail}") from exc
        except (urllib.error.URLError, OSError) as exc:
            raise HubError("companion_unavailable", "连不上本地大脑") from exc
        try:
            body = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise HubError("companion_unavailable", "大脑返回无法解析") from exc
        message = body.get("message") if isinstance(body, dict) else None
        if not isinstance(message, dict):
            raise HubError("companion_unavailable", "大脑返回无法解析")
        return str(message.get("content") or "")

    def _ping_tts(self) -> bool:
        url = _join(self.config.tts_base_url or "", "/health")
        try:
            req = urllib.request.Request(url, method="GET")
            with urllib.request.urlopen(req, timeout=0.4) as resp:
                if not (200 <= resp.status < 300):
                    return False
                raw = resp.read().decode("utf-8")
        except (urllib.error.URLError, TimeoutError, OSError):
            return False
        try:
            body = json.loads(raw)
        except json.JSONDecodeError:
            return False
        return isinstance(body, dict) and bool(body.get("ready"))

    def _speak(self, text: str, turn: str) -> bytes | None:
        url = _join(self.config.tts_base_url or "", "/v1/speak")
        play_only = not self.config.tts_deliver
        body: dict[str, Any] = {
            "text": text,
            "voice": "shorekeeper",
            "language": "chinese",
            "turn_id": turn,
        }
        if play_only:
            body["play_only"] = True
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        accept = "application/json" if play_only else "audio/wav"
        timeout = 3.0 if play_only else self.config.tts_timeout_sec
        req = urllib.request.Request(
            url,
            data=payload,
            method="POST",
            headers={"Content-Type": "application/json", "Accept": accept},
        )
        tts_at = time.monotonic()
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read()
        except (TimeoutError, urllib.error.URLError, OSError):
            _latency(turn, "tts_error", ms=_ms(tts_at))
            return None
        if play_only:
            _latency(turn, "tts_cued", ms=_ms(tts_at), bytes=len(raw))
            return None
        if not raw:
            _latency(turn, "tts_empty", ms=_ms(tts_at))
            return None
        _latency(turn, "tts_done", ms=_ms(tts_at), bytes=len(raw))
        return raw


def facts_from_snapshot(snap: dict[str, Any]) -> str:
    lines = []
    temp = snap.get("temperature")
    if isinstance(temp, dict) and temp.get("celsius") is not None:
        extra = ""
        if temp.get("humidity") is not None:
            extra = f"、湿度 {temp['humidity']}%"
        lines.append(f"室内 {temp['celsius']}°C{extra}。")
    pc = snap.get("pc")
    if isinstance(pc, dict) and pc.get("online"):
        cpu = pc.get("cpu") or {}
        gpu = pc.get("gpu") or {}
        mem = pc.get("memory") or {}
        bits = []
        if cpu.get("percent") is not None:
            bits.append(f"CPU {cpu['percent']}%")
        if mem.get("percent") is not None:
            bits.append(f"内存 {mem['percent']}%")
        if isinstance(gpu, dict) and gpu.get("percent") is not None:
            bits.append(f"GPU {gpu['percent']}%")
        if bits:
            lines.append("电脑：" + "，".join(bits) + "。")
    devices = snap.get("devices") or []
    lamps = []
    for item in devices:
        if not isinstance(item, dict):
            continue
        if item.get("type") not in {"light", "switch"}:
            continue
        name = item.get("name") or item.get("id")
        on = "开" if item.get("on") else "关"
        lamps.append(f"{name}{on}")
    if lamps:
        lines.append("设备：" + "，".join(lamps) + "。")
    return "\n".join(lines) if lines else "没有更多传感器读数。"


def _clean_reply(raw: str) -> str:
    text = _THINK_RE.sub("", raw)
    text = _ACTION_RE.sub("", text)
    text = re.sub(r"\n{2,}", "\n", text).strip()
    return text


def _turn_id(body: dict[str, Any]) -> str:
    raw = str(body.get("turn_id") or "").strip()
    if raw and ID_RE.match(raw) and len(raw) <= 64:
        return raw
    return uuid.uuid4().hex[:12]


def _ms(started: float) -> int:
    return max(0, int((time.monotonic() - started) * 1000))


def _latency(turn: str, stage: str, **fields: Any) -> None:
    stamp = time.strftime("%H:%M:%S")
    parts = [f"latency {stamp} turn={turn} stage={stage}"]
    parts.extend(f"{key}={value}" for key, value in fields.items())
    print(" ".join(parts), flush=True)


def _join(base: str, path: str) -> str:
    parsed = urlparse(base)
    root = urlunparse((parsed.scheme or "http", parsed.netloc or parsed.path, "", "", "", ""))
    return root.rstrip("/") + path
