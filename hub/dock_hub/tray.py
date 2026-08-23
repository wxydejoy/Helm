"""Windows system tray for Dock Hub."""

from __future__ import annotations

import subprocess
import sys
import threading
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


def _message_box(title: str, text: str) -> None:
    try:
        import ctypes

        ctypes.windll.user32.MessageBoxW(0, text, title, 0x40)
    except Exception:
        print(f"{title}: {text}", flush=True)


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

    def open_login_hint(icon: pystray.Icon, _item: object) -> None:
        hint = CONFIG_DIR / "mijia-login.txt"
        if hint.is_file():
            subprocess.Popen(["notepad.exe", str(hint)], close_fds=True)
        else:
            _message_box("Dock Hub", "还没有登录链接。请点「重新登录米家」。")

    def relogin_mijia(icon: pystray.Icon, _item: object) -> None:
        def work() -> None:
            try:
                _message_box(
                    "Dock Hub",
                    "即将打开浏览器二维码。\n请用【米家 App】扫码，扫完后等几秒即可。",
                )
                hub.relogin()
                status, msg = hub._mijia_status()  # noqa: SLF001
                if status == "ok":
                    _message_box("Dock Hub", "米家登录成功。")
                else:
                    _message_box("Dock Hub", msg or "米家仍未登录。")
            except Exception as exc:
                _message_box("Dock Hub", f"重新登录失败：{exc}")

        threading.Thread(target=work, name="mijia-relogin", daemon=True).start()

    def quit_app(icon: pystray.Icon, _item: object) -> None:
        icon.stop()
        stop_server()

    menu = pystray.Menu(
        Item(status_line, None, enabled=False),
        Item("打开 /health", open_health),
        Item("重新登录米家", relogin_mijia),
        Item("打开米家登录链接", open_login_hint),
        Item("打开日志", open_log),
        Item("打开配置目录", open_config),
        pystray.Menu.SEPARATOR,
        Item("退出", quit_app),
    )
    icon = pystray.Icon("dock-hub", load_tray_image(), "Dock Hub", menu)
    # pystray.run() blocks; HTTP server must already be on another thread
    icon.run()
