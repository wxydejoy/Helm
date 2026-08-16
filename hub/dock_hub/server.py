from __future__ import annotations

import json
import os
import socket
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import unquote, urlparse

from dock_hub.errors import HubError
from dock_hub.service import DockHub

MAX_BODY = 64 * 1024


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

    # 先探测 10.83.22 网段（安卓在这边），再走默认路由，避免只打出 Wi-Fi 的 192.168
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


def make_handler(hub: DockHub) -> type[BaseHTTPRequestHandler]:
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt: str, *args: Any) -> None:
            if os.environ.get("DOCK_HUB_ACCESS_LOG"):
                print(f"{self.address_string()} {fmt % args}")

        def do_GET(self) -> None:  # noqa: N802
            parsed = urlparse(self.path)
            path = parsed.path.rstrip("/") or "/"
            try:
                if path == "/health":
                    self._json(200, hub.health())
                    return
                if path == "/v1/snapshot":
                    self._require_auth()
                    self._json(200, hub.snapshot())
                    return
                raise HubError("not_found", "未知接口")
            except HubError as exc:
                self._json(exc.status, exc.body())

        def do_POST(self) -> None:  # noqa: N802
            parsed = urlparse(self.path)
            path = parsed.path.rstrip("/") or "/"
            try:
                prefix = "/v1/devices/"
                suffix = "/command"
                if not (path.startswith(prefix) and path.endswith(suffix)):
                    raise HubError("not_found", "未知接口")
                device_id = unquote(path[len(prefix) : -len(suffix)])
                if not device_id or "/" in device_id:
                    raise HubError("not_found", "未知接口")
                self._require_auth()
                body = self._read_json()
                self._json(200, hub.command(device_id, body))
            except HubError as exc:
                self._json(exc.status, exc.body())

        def _require_auth(self) -> None:
            header = self.headers.get("Authorization") or self.headers.get("authorization") or ""
            expected = f"Bearer {hub.config.token}"
            if header.strip() != expected:
                raise HubError("unauthorized", "Token 不正确")

        def _read_json(self) -> dict[str, Any]:
            length_raw = self.headers.get("Content-Length", "0")
            try:
                length = int(length_raw)
            except ValueError as exc:
                raise HubError("bad_request", "JSON 无效") from exc
            if length < 0 or length > MAX_BODY:
                raise HubError("bad_request", "请求体过大")
            raw = self.rfile.read(length) if length else b"{}"
            if not raw:
                return {}
            try:
                data = json.loads(raw.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise HubError("bad_request", "JSON 无效") from exc
            if not isinstance(data, dict):
                raise HubError("bad_request", "JSON 必须是对象")
            return data

        def _json(self, status: int, payload: dict[str, Any]) -> None:
            data = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

    return Handler


def serve(hub: DockHub, *, blocking: bool = True) -> ThreadingHTTPServer:
    handler = make_handler(hub)
    server = ThreadingHTTPServer((hub.config.host, hub.config.port), handler)
    ips = lan_ips()
    print(f"Helm v1  http://{ips[0]}:{hub.config.port}", flush=True)
    for extra in ips[1:]:
        print(f"             http://{extra}:{hub.config.port}", flush=True)
    if hub.config.path:
        print(f"配置         {hub.config.path}", flush=True)
    print("Windows 防火墙请放行入站 TCP 17890：", flush=True)
    print(
        '  netsh advfirewall firewall add rule name="Helm" '
        "dir=in action=allow protocol=TCP localport=17890",
        flush=True,
    )
    if not blocking:
        return server
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nHub 已停止")
        server.server_close()
    return server
