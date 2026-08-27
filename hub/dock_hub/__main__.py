from __future__ import annotations

import argparse
import secrets
import sys
import threading
from pathlib import Path

LOG_PATH = Path.home() / ".config" / "dock-hub" / "hub.log"


def _attach_background_log() -> None:
    stdout = sys.stdout
    if stdout is not None and getattr(stdout, "isatty", lambda: False)():
        if hasattr(stdout, "reconfigure"):
            stdout.reconfigure(encoding="utf-8", line_buffering=True)
            sys.stderr.reconfigure(encoding="utf-8", line_buffering=True)
        return
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    stream = open(LOG_PATH, "a", encoding="utf-8", buffering=1)
    sys.stdout = stream
    sys.stderr = stream


# pythonw / 无控制台 exe 下 stdout 可能是 None；mijiaAPI import 会调 isatty()
_attach_background_log()

from dock_hub.config import ensure_config_file, load_config
from dock_hub.server import lan_ips, serve
from dock_hub.service import DockHub


def main(argv: list[str] | None = None) -> int:
    try:
        return _run(argv)
    except KeyboardInterrupt:
        print("\nHub 已停止", flush=True)
        return 0
    except Exception:
        import traceback

        traceback.print_exc()
        return 1


def _run(argv: list[str] | None) -> int:
    parser = argparse.ArgumentParser(prog="dock-hub", description="Shoreting LAN Protocol v1 Hub")
    parser.add_argument("--config", type=Path, help="hub.yaml 路径")
    parser.add_argument("--init", action="store_true", help="生成一份带随机 token 的配置")
    parser.add_argument("--list-devices", action="store_true", help="列出米家设备名")
    parser.add_argument("--dump-device", metavar="NAME", help="打印某个米家设备的属性名")
    parser.add_argument(
        "--no-tray",
        action="store_true",
        help="不要系统托盘（前台控制台运行，Ctrl+C 退出）",
    )
    parser.add_argument(
        "--login",
        action="store_true",
        help="强制重新扫码登录米家后退出（会打开浏览器二维码）",
    )
    args = parser.parse_args(argv)

    if args.init:
        return _init_config(args.config)
    if args.login:
        return _force_login()
    if args.list_devices:
        return _list_devices()
    if args.dump_device:
        return _dump_device(args.dump_device)

    try:
        if args.config:
            config = load_config(args.config)
        else:
            config = load_config(ensure_config_file(None))
    except Exception as exc:
        print(exc, file=sys.stderr)
        return 1

    hub = DockHub(config)
    use_tray = sys.platform == "win32" and not args.no_tray
    if use_tray:
        # 先出托盘，登录在后台（过期时会弹浏览器二维码，也可托盘「重新登录米家」）
        return _run_with_tray(hub, login_async=True)

    print("正在检查米家登录（首次会打开浏览器二维码，用米家 App 扫）…", flush=True)
    hub.login()
    serve(hub, blocking=True)
    return 0


def _force_login() -> int:
    from dock_hub.mijia_bridge import login_or_qr

    print("强制重新登录米家（将打开浏览器二维码）…", flush=True)
    try:
        login_or_qr(force=True)
    except Exception as exc:
        print(f"登录失败：{exc}", file=sys.stderr)
        return 1
    print("完成。可重新启动岸亭 Hub。", flush=True)
    return 0


def _run_with_tray(hub: DockHub, *, login_async: bool = False) -> int:
    from dock_hub.setup_ui import open_setup_browser
    from dock_hub.tray import run_tray

    server = serve(hub, blocking=False)
    thread = threading.Thread(target=server.serve_forever, name="dock-http", daemon=True)
    thread.start()

    threading.Timer(1.2, lambda: open_setup_browser(hub)).start()

    if login_async:
        print("正在检查米家登录（需要时会打开浏览器二维码）…", flush=True)

        def boot_login() -> None:
            try:
                hub.login()
            except Exception as exc:
                print(f"米家登录异常：{exc}", flush=True)

        threading.Thread(target=boot_login, name="mijia-boot-login", daemon=True).start()
    else:
        hub.login()

    ips = lan_ips()
    status = f"监听 {ips[0]}:{hub.config.port}"

    def stop() -> None:
        print("Hub 已停止", flush=True)
        try:
            hub.pc.stop()
        except Exception:
            pass
        server.shutdown()
        server.server_close()

    run_tray(hub, stop, status)
    return 0


def _init_config(explicit: Path | None) -> int:
    dest = explicit or Path.home() / ".config" / "dock-hub" / "hub.yaml"
    if dest.exists():
        print(f"已存在：{dest}")
        return 1
    # frozen exe: example next to exe or bundled
    if getattr(sys, "frozen", False):
        example = Path(sys.executable).resolve().parent / "hub.yaml.example"
        if not example.is_file() and hasattr(sys, "_MEIPASS"):
            example = Path(sys._MEIPASS) / "hub.yaml.example"  # type: ignore[attr-defined]
    else:
        example = Path(__file__).resolve().parent.parent / "hub.yaml.example"
    dest.parent.mkdir(parents=True, exist_ok=True)
    text = example.read_text(encoding="utf-8")
    token = secrets.token_urlsafe(32)
    text = text.replace("replace-with-a-long-random-string", token)
    dest.write_text(text, encoding="utf-8")
    print(f"已写入 {dest}")
    print(f"token = {token}")
    print("请改 mijia_name / path 后运行 dock-hub --list-devices 核对设备名。")
    return 0


def _list_devices() -> int:
    from mijiaAPI import mijiaAPI

    from dock_hub.mijia_bridge import login_or_qr

    try:
        api = login_or_qr(mijiaAPI())
        devices = api.get_devices_list()
    except Exception as exc:
        print(f"无法列出设备：{exc}", file=sys.stderr)
        return 1
    if not devices:
        print("米家里没有设备")
        return 0
    width = max(len(str(d.get("name", ""))) for d in devices)
    for item in devices:
        name = str(item.get("name", ""))
        model = item.get("model", "")
        online = item.get("isOnline", item.get("is_online", ""))
        print(f"{name.ljust(width)}  {model}  online={online}")
    return 0


def _dump_device(name: str) -> int:
    from mijiaAPI import mijiaAPI, mijiaDevice

    from dock_hub.mijia_bridge import login_or_qr

    try:
        api = login_or_qr(mijiaAPI())
        device = mijiaDevice(api, dev_name=name)
    except Exception as exc:
        print(f"无法读取设备：{exc}", file=sys.stderr)
        return 1
    print(device)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
