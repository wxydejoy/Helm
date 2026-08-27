from __future__ import annotations

import json
import os
import socket
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import urlparse

from helm_mini.sampler import MiniSampler

PROTOCOL = 1
SERVICE = "helm-mini"


def lan_ips() -> list[str]:
    found: list[str] = []

    def add(ip: str) -> None:
        if (
            ip
            and ip not in found
            and not ip.startswith("127.")
            and not ip.startswith("169.254.")
        ):
            found.append(ip)

    for probe in ("10.83.22.1", "223.5.5.5", "8.8.8.8"):
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.settimeout(0.3)
            sock.connect((probe, 80))
            add(sock.getsockname()[0])
            sock.close()
        except OSError:
            continue
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            add(info[4][0])
    except OSError:
        pass
    found.sort(key=lambda ip: (0 if ip.startswith("10.83.22.") else 1, ip))
    return found or ["127.0.0.1"]


def make_handler(sampler: MiniSampler, token: str, name: str) -> type[BaseHTTPRequestHandler]:
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt: str, *args: Any) -> None:
            if os.environ.get("HELM_MINI_ACCESS_LOG"):
                print(f"{self.address_string()} {fmt % args}")

        def do_GET(self) -> None:  # noqa: N802
            path = urlparse(self.path).path.rstrip("/") or "/"
            try:
                if path == "/health":
                    self._json(
                        200,
                        {
                            "ok": True,
                            "service": SERVICE,
                            "protocol": PROTOCOL,
                            "name": name,
                        },
                    )
                    return
                if path == "/v1/snapshot":
                    self._require_auth()
                    self._json(
                        200,
                        {
                            "protocol": PROTOCOL,
                            "service": SERVICE,
                            "pc": sampler.snapshot(),
                        },
                    )
                    return
                self._error(404, "not_found", "未知接口")
            except AuthError as exc:
                self._error(exc.status, exc.code, exc.message)

        def _require_auth(self) -> None:
            if not token:
                return
            header = self.headers.get("Authorization", "")
            expected = f"Bearer {token}"
            if header != expected:
                raise AuthError(401, "unauthorized", "Token 不正确")

        def _json(self, status: int, body: dict[str, Any]) -> None:
            raw = json.dumps(body, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(raw)

        def _error(self, status: int, code: str, message: str) -> None:
            self._json(status, {"error": {"code": code, "message": message}})

    return Handler


class AuthError(Exception):
    def __init__(self, status: int, code: str, message: str) -> None:
        self.status = status
        self.code = code
        self.message = message


def serve(host: str, port: int, sampler: MiniSampler, token: str, name: str) -> ThreadingHTTPServer:
    httpd = ThreadingHTTPServer((host, port), make_handler(sampler, token, name))
    return httpd
