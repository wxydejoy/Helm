"""Windows system tray for Helm Hub."""

from __future__ import annotations

import subprocess
import sys
import webbrowser
from pathlib import Path
from typing import TYPE_CHECKING, Callable

if TYPE_CHECKING:
    from dock_hub.service import DockHub

LOG_PATH = Path.home() / ".config" / "dock-hub" / "hub.log"
CONFIG_DIR = Path.home() / ".config" / "dock-hub"


def _resource_path(name: str) -> Path:
    if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
        return Path(sys._MEIPASS) / name  # type: ignore[attr-defined]
    return Path(__file__).resolve().parent / "assets" / name


def load_tray_image():
    from PIL import Image

    icon = _resource_path("icon.png")
    if icon.is_file():
        return Image.open(icon)
    # fallback: solid mark
    img = Image.new("RGBA", (64, 64), (32, 120, 200, 255))
    return img


def run_tray(hub: "DockHub", stop_server: Callable[[], None], status_line: str) -> None:
    """Block on the tray event loop until the user quits."""
    import pystray
    from pystray import MenuItem as Item

    def open_log(icon: pystray.Icon, _item: object) -> None:
        LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
        if not LOG_PATH.exists():
            LOG_PATH.write_text("", encoding="utf-8")
        subprocess.Popen(["notepad.exe", str(LOG_PATH)], close_fds=True)

    def open_config(icon: pystray.Icon, _item: object) -> None:
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        subprocess.Popen(["explorer.exe", str(CONFIG_DIR)], close_fds=True)

    def open_health(icon: pystray.Icon, _item: object) -> None:
        port = hub.config.port
        webbrowser.open(f"http://127.0.0.1:{port}/health")

    def quit_app(icon: pystray.Icon, _item: object) -> None:
        icon.stop()
        stop_server()

    menu = pystray.Menu(
        Item(status_line, None, enabled=False),
        Item("打开 /health", open_health),
        Item("打开日志", open_log),
        Item("打开配置目录", open_config),
        pystray.Menu.SEPARATOR,
        Item("退出", quit_app),
    )
    icon = pystray.Icon("dock-hub", load_tray_image(), "Helm", menu)
    # pystray.run() blocks; HTTP server must already be on another thread
    icon.run()
