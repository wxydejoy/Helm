from __future__ import annotations

import json
import tempfile
import threading
import unittest
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer
from unittest.mock import patch
from urllib.error import URLError
from urllib.request import Request

from dock_hub.companion import _clean_reply, facts_from_snapshot
from dock_hub.config import hub_config_to_raw, parse_config
from dock_hub.server import make_handler
from dock_hub.service import DockHub


class _FakeResp:
    def __init__(self, payload: dict, status: int = 200) -> None:
        self.status = status
        self._payload = payload

    def read(self) -> bytes:
        return json.dumps(self._payload).encode("utf-8")

    def __enter__(self) -> "_FakeResp":
        return self

    def __exit__(self, *args: object) -> bool:
        return False


class _FakeBytes:
    def __init__(self, payload: bytes, status: int = 200) -> None:
        self.status = status
        self._payload = payload

    def read(self) -> bytes:
        return self._payload

    def __enter__(self) -> "_FakeBytes":
        return self

    def __exit__(self, *args: object) -> bool:
        return False


def _fake_ollama(content: str = "晚上好，漂泊者。", wav: bytes | None = None):
    def urlopen(req: Request, timeout: object = None) -> _FakeResp | _FakeBytes:
        url = req.get_full_url()
        if url.endswith("/api/tags"):
            return _FakeResp({"models": [{"name": "qwen3.5:4b"}]})
        if url.endswith("/api/chat"):
            return _FakeResp({"message": {"content": content}})
        if url.endswith("/health"):
            return _FakeResp({"ok": True, "ready": True, "service": "helm-companion-tts"})
        if url.endswith("/v1/speak"):
            if wav is None:
                raise URLError("tts down")
            return _FakeBytes(wav)
        raise AssertionError(url)

    return urlopen


class CompanionLogicTest(unittest.TestCase):
    def test_strips_think_and_action(self) -> None:
        raw = "<think>long</think>\n晚上好。\nACTION: lamp.on\n还早。"
        self.assertEqual(_clean_reply(raw), "晚上好。\n还早。")

    def test_facts_from_snapshot(self) -> None:
        facts = facts_from_snapshot(
            {
                "temperature": {"celsius": 24.0, "humidity": 50},
                "pc": {
                    "online": True,
                    "cpu": {"percent": 10},
                    "memory": {"percent": 40},
                    "gpu": {"percent": 5},
                },
                "devices": [
                    {"type": "light", "name": "台灯", "on": True},
                    {"type": "action", "name": "Steam", "on": False},
                ],
            }
        )
        self.assertIn("室内 24.0°C、湿度 50%", facts)
        self.assertIn("CPU 10%", facts)
        self.assertIn("台灯开", facts)
        self.assertNotIn("Steam", facts)

    def test_parse_and_dump_companion(self) -> None:
        cfg = parse_config(
            {
                "name": "study",
                "token": "secret-token-value",
                "companion": {
                    "enabled": True,
                    "llm": {"base_url": "http://10.0.0.8:11434", "model": "qwen3.5:4b"},
                    "tts": {"base_url": "http://10.0.0.8:18100"},
                },
                "devices": [],
            }
        )
        self.assertTrue(cfg.companion.enabled)
        self.assertEqual(cfg.companion.llm_base_url, "http://10.0.0.8:11434")
        self.assertEqual(cfg.companion.tts_base_url, "http://10.0.0.8:18100")
        dumped = hub_config_to_raw(cfg)
        self.assertEqual(dumped["companion"]["llm"]["model"], "qwen3.5:4b")
        self.assertEqual(dumped["companion"]["tts"]["base_url"], "http://10.0.0.8:18100")


class CompanionProtocolTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        cfg = parse_config(
            {
                "name": "study",
                "token": "secret-token-value",
                "host": "127.0.0.1",
                "port": 0,
                "companion": {
                    "enabled": True,
                    "llm": {
                        "base_url": "http://127.0.0.1:9",
                        "model": "qwen3.5:4b",
                        "timeout_sec": 2,
                    },
                },
                "devices": [],
            }
        )
        self.hub = DockHub(cfg)
        handler = make_handler(self.hub)
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        self.port = self.server.server_address[1]

    def _request(self, method: str, path: str, body: dict | None = None):
        conn = HTTPConnection("127.0.0.1", self.port, timeout=5)
        headers = {
            "Accept": "application/json",
            "Authorization": "Bearer secret-token-value",
        }
        raw = None
        if body is not None:
            raw = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        conn.request(method, path, body=raw, headers=headers)
        resp = conn.getresponse()
        payload = json.loads(resp.read().decode("utf-8"))
        conn.close()
        return resp.status, payload

    def _raw(self, method: str, path: str):
        conn = HTTPConnection("127.0.0.1", self.port, timeout=5)
        headers = {
            "Accept": "*/*",
            "Authorization": "Bearer secret-token-value",
        }
        conn.request(method, path, headers=headers)
        resp = conn.getresponse()
        payload = resp.read()
        content_type = resp.getheader("Content-Type")
        status = resp.status
        conn.close()
        return status, content_type, payload

    def test_snapshot_ready_false_when_ollama_down(self) -> None:
        status, body = self._request("GET", "/v1/snapshot")
        self.assertEqual(status, 200)
        self.assertEqual(body["companion"]["ready"], False)
        self.assertFalse(body["companion"]["voice"])

    def test_chat_502_when_ollama_down(self) -> None:
        status, body = self._request("POST", "/v1/companion/chat", {"text": "晚上好。"})
        self.assertEqual(status, 502)
        self.assertEqual(body["error"]["code"], "companion_unavailable")

    def test_chat_empty_text(self) -> None:
        status, body = self._request("POST", "/v1/companion/chat", {"text": "  "})
        self.assertEqual(status, 400)
        self.assertEqual(body["error"]["code"], "bad_request")

    def test_chat_ok_strips_action(self) -> None:
        fake = _fake_ollama("晚上好，漂泊者。\nACTION: lamp.on")
        with patch("dock_hub.companion.urllib.request.urlopen", fake):
            status, body = self._request("POST", "/v1/companion/chat", {"text": "晚上好。"})
        self.assertEqual(status, 200)
        self.assertEqual(body["text"], "晚上好，漂泊者。")
        self.assertIsNone(body["audio_id"])
        self.assertNotIn("ACTION", body["text"])

    def test_chat_with_tts_returns_audio(self) -> None:
        wav = b"RIFF....WAVE"
        fake = _fake_ollama("晚上好，漂泊者。", wav=wav)
        self.hub.companion.configure(
            parse_config(
                {
                    "name": "study",
                    "token": "secret-token-value",
                    "companion": {
                        "enabled": True,
                        "llm": {
                            "base_url": "http://127.0.0.1:9",
                            "model": "qwen3.5:4b",
                            "timeout_sec": 2,
                        },
                        "tts": {"base_url": "http://127.0.0.1:9", "timeout_sec": 2},
                    },
                    "devices": [],
                }
            ).companion
        )
        with patch("dock_hub.companion.urllib.request.urlopen", fake):
            status, body = self._request("POST", "/v1/companion/chat", {"text": "晚上好。"})
            self.assertEqual(status, 200)
            self.assertEqual(body["text"], "晚上好，漂泊者。")
            audio_id = body["audio_id"]
            self.assertIsInstance(audio_id, str)
            self.assertTrue(audio_id)
            snap_status, snap = self._request("GET", "/v1/snapshot")
            self.assertEqual(snap_status, 200)
            self.assertTrue(snap["companion"]["voice"])
            wav_status, ctype, payload = self._raw("GET", f"/v1/companion/audio/{audio_id}")
        self.assertEqual(wav_status, 200)
        self.assertEqual(ctype, "audio/wav")
        self.assertEqual(payload, wav)

    def test_chat_uses_turn_id_as_audio_id(self) -> None:
        wav = b"RIFF....WAVE"
        fake = _fake_ollama("晚上好，漂泊者。", wav=wav)
        self.hub.companion.configure(
            parse_config(
                {
                    "name": "study",
                    "token": "secret-token-value",
                    "companion": {
                        "enabled": True,
                        "llm": {
                            "base_url": "http://127.0.0.1:9",
                            "model": "qwen3.5:4b",
                            "timeout_sec": 2,
                        },
                        "tts": {"base_url": "http://127.0.0.1:9", "timeout_sec": 2},
                    },
                    "devices": [],
                }
            ).companion
        )
        with patch("dock_hub.companion.urllib.request.urlopen", fake):
            status, body = self._request(
                "POST",
                "/v1/companion/chat",
                {"text": "晚上好。", "turn_id": "lat12ab34cd"},
            )
        self.assertEqual(status, 200)
        self.assertEqual(body["audio_id"], "lat12ab34cd")

    def test_unknown_audio_is_404(self) -> None:
        status, body = self._request("GET", "/v1/companion/audio/nope")
        self.assertEqual(status, 404)
        self.assertEqual(body["error"]["code"], "not_found")

    def test_bad_audio_id_is_404(self) -> None:
        status, body = self._request("GET", "/v1/companion/audio/../x")
        self.assertEqual(status, 404)
        self.assertEqual(body["error"]["code"], "not_found")

    def test_tts_down_still_returns_text(self) -> None:
        fake = _fake_ollama("岸边很安静。")
        self.hub.companion.configure(
            parse_config(
                {
                    "name": "study",
                    "token": "secret-token-value",
                    "companion": {
                        "enabled": True,
                        "llm": {
                            "base_url": "http://127.0.0.1:9",
                            "model": "qwen3.5:4b",
                            "timeout_sec": 2,
                        },
                        "tts": {"base_url": "http://127.0.0.1:9"},
                    },
                    "devices": [],
                }
            ).companion
        )
        with patch("dock_hub.companion.urllib.request.urlopen", fake):
            status, body = self._request("POST", "/v1/companion/chat", {"text": "晚上好。"})
        self.assertEqual(status, 200)
        self.assertEqual(body["text"], "岸边很安静。")
        self.assertIsNone(body["audio_id"])


if __name__ == "__main__":
    unittest.main()
