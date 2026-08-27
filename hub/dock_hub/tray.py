"""Windows system tray for 岸亭 Hub."""

from __future__ import annotations

import sys
from pathlib import Path
from typing import TYPE_CHECKING, Callable

if TYPE_CHECKING:
    from dock_hub.service import DockHub


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

    def open_setup(icon: pystray.Icon, _item: object) -> None:
        from dock_hub.setup_ui import open_setup_browser

        open_setup_browser(hub, force=True)

    def quit_app(icon: pystray.Icon, _item: object) -> None:
        icon.stop()
        stop_server()

    menu = pystray.Menu(
        Item(status_line, None, enabled=False),
        Item("打开配置向导", open_setup),
        pystray.Menu.SEPARATOR,
        Item("退出", quit_app),
    )
    icon = pystray.Icon("dock-hub", load_tray_image(), "Shoreting Hub", menu)
    icon.run()
