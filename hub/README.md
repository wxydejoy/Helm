# Dock Hub（Windows）

电脑上的 Python 服务：对内调 [mijia-api](https://github.com/Do1e/mijia-api)、采集本机性能、按白名单启动本机程序，并作为本地大脑 / TTS 的客户端；对外只提供 [Dock LAN Protocol v1](../docs/lan-protocol.md) 的 HTTP 接口。

安卓不直连米家，也看不到 `did` / 可执行文件路径。依赖 GPL-3.0 的 mijia-api：自用没问题；若分发 Hub，需要同样开源。

## 目录结构

```
hub/
  dock_hub/           # 源码包
    __main__.py       # CLI 入口（托盘 / 控制台）
    tray.py           # 系统托盘
    server.py         # HTTP
    service.py        # snapshot / command
    pc.py             # CPU / 内存 / GPU / CPU 温度
    assets/           # 托盘与 exe 图标
  dock_hub_entry.py   # PyInstaller 入口
  dock-hub.spec       # PyInstaller 规格
  build-exe.ps1       # 打成 dist/DockHub.exe
  run-admin.ps1       # 管理员启动（优先 exe）
  run_hub.pyw         # 无控制台源码启动（旧路径兼容）
  hub.yaml.example
  tests/
```

配置与日志（不进仓库）：

| 路径 | 用途 |
|------|------|
| `%USERPROFILE%\.config\dock-hub\hub.yaml` | 主配置 + token |
| `%USERPROFILE%\.config\dock-hub\hub.log` | 无控制台时的运行日志 |
| `%USERPROFILE%\.config\mijia-api\auth.json` | 米家登录缓存（勿发给手机） |

## 要求

- Windows 10/11
- Python 3.10+（推荐 [uv](https://github.com/astral-sh/uv)）
- 防火墙放行入站 **TCP 17890**
- CPU 温度（可选）：见下方 [PawnIO 安装](#pawnio-cpu-温度可选)

## 日常用法（推荐 exe）

```powershell
cd hub
powershell -ExecutionPolicy Bypass -File .\build-exe.ps1
```

产物：`hub\dist\DockHub.exe`（无黑窗口 + 系统托盘）。

1. **右键 → 以管理员身份运行**（或 `.\run-admin.ps1`，有 exe 时会优先启动它）
2. 托盘右键：**打开配置向导** 或 **退出**（首次启动会自动打开向导）
3. 配置向导地址：`http://127.0.0.1:17890/setup`（**仅本机**可访问）
4. 安卓填向导里显示的 IP、端口 `17890`、Token

重新打包前先退出托盘里的旧进程。

## 源码开发

```powershell
cd hub
uv sync
uv run dock-hub                      # 首次会自动生成 hub.yaml 并打开配置向导
uv run dock-hub --init              # 仅命令行：生成随机 token 的 hub.yaml
uv run dock-hub --list-devices      # 核对米家设备名
uv run dock-hub --dump-device "卧室温湿度计"
uv run dock-hub                     # 默认系统托盘
uv run dock-hub --no-tray           # 前台控制台，Ctrl+C 退出
uv run dock-hub --login             # 强制重新扫码登录米家（打开浏览器二维码）后退出
```

米家过期时：在配置向导里点 **「扫码登录米家」**，或运行 `--login`（命令行仍会打开浏览器）。

管理员无窗口（源码或 exe）：

```powershell
powershell -ExecutionPolicy Bypass -File .\run-admin.ps1
```

### 配置向导（推荐小白）

浏览器打开 `http://127.0.0.1:17890/setup`（托盘 → **打开配置向导**）。可图形化完成：

1. 复制 **IP / Token** 到安卓
2. 米家扫码登录
3. 选温度源、灯/开关、启动程序
4. 修改后**自动保存**（改端口后需重启 Hub）

仍可直接编辑 `%USERPROFILE%\.config\dock-hub\hub.yaml`。

### 配置要点（`hub.yaml`）

- `token`：安卓 Bearer；`--init` 已生成
- `temperature`：只允许一个；没有就删整段
- `devices`：`light` / `switch` 用 `mijia_name`；`action` 用本机 `run.program`（路径不发给手机）
- `icon`：Remix 短名（如 `steam`、`cursor`、`computer`）；安卓据此画图标
- `pc.enabled: true`：snapshot 带 CPU / 内存；有 NVIDIA 再带 GPU；CPU 温度需管理员 + PawnIO（见下）

### PawnIO（CPU 温度，可选）

**是什么：** [PawnIO](https://pawnio.eu/) 是 Windows 上的小型驱动，让程序能读取 CPU 温度传感器。Hub 用它把温度显示在安卓主屏；**不装也能正常用** Hub 的其他功能，只是没有 CPU 温度这一项。

**还需要：** Dock Hub 必须以**管理员**运行（右键 `DockHub.exe` → 以管理员身份运行，或 `run-admin.ps1`）。

**安装（任选一种）：**

```powershell
# 管理员 PowerShell / 终端
winget install -e --id namazso.PawnIO
```

或从 [GitHub Release](https://github.com/namazso/PawnIO/releases) / [pawnio.eu](https://pawnio.eu/) 下载安装包手动安装。

装完后退出 Hub，再**以管理员**重新启动。配置向导第 ⑤ 步也有同样说明。

示例见 `hub.yaml.example`。

### 防火墙（管理员 PowerShell）

```powershell
netsh advfirewall firewall add rule name="Dock Hub" dir=in action=allow protocol=TCP localport=17890
```

## 自测

把 `HOST`、`TOKEN` 换成实际值：

```powershell
curl.exe -s http://HOST:17890/health
curl.exe -s http://HOST:17890/v1/snapshot -H "Authorization: Bearer TOKEN"
curl.exe -s http://HOST:17890/v1/devices/lamp/command `
  -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" `
  -d "{\"on\":true,\"brightness\":80}"
curl.exe -s http://HOST:17890/v1/devices/steam/command `
  -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" `
  -d "{\"run\":true}"
```

`/health` 不必带 token。snapshot 不带 token 应为 401。

```powershell
uv run dock-hub --config D:\path\to\hub.yaml
uv run python -m unittest discover -s tests -v
```

## 同步到 Mac 仓库

本仓库权威副本在 Mac：`weiekko@10.83.22.121:/Users/weiekko/dock/`（IP 会变；SSH 不通时先看 ARP / known_hosts）。  
Windows 改完 Hub 后 scp 源码与说明（不要传 `.venv` / `build` / `dist`）：

```powershell
$mac = "weiekko@10.83.22.121"
ssh $mac "mkdir -p /Users/weiekko/dock/hub"
scp -r dock_hub tests pyproject.toml uv.lock README.md hub.yaml.example .gitignore `
  run-admin.ps1 run_hub.pyw build-exe.ps1 dock-hub.spec dock_hub_entry.py `
  ${mac}:/Users/weiekko/dock/hub/
scp ..\README.md ${mac}:/Users/weiekko/dock/README.md
```
