# Dock Hub（Windows）

电脑上的 Python 服务：对内调 [mijia-api](https://github.com/Do1e/mijia-api)、采集本机性能、按白名单启动本机程序；对外只提供 [Dock LAN Protocol v1](../docs/lan-protocol.md) 的 3 个 HTTP 接口。

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
- CPU 温度：安装 [PawnIO](https://github.com/namazso/PawnIO)（`winget install namazso.PawnIO`），并以**管理员**运行 Hub

## 日常用法（推荐 exe）

```powershell
cd hub
powershell -ExecutionPolicy Bypass -File .\build-exe.ps1
```

产物：`hub\dist\DockHub.exe`（无黑窗口 + 系统托盘）。

1. **右键 → 以管理员身份运行**（或 `.\run-admin.ps1`，有 exe 时会优先启动它）
2. 托盘菜单：打开 `/health`、日志、配置目录、退出
3. 安卓填局域网 IP（本机常见 `10.83.22.31`）、端口 `17890`、yaml 里的 token

重新打包前先退出托盘里的旧进程。

## 源码开发

```powershell
cd hub
uv sync
uv run dock-hub --init              # 首次：生成随机 token 的 hub.yaml
uv run dock-hub --list-devices      # 核对米家设备名
uv run dock-hub --dump-device "卧室温湿度计"
uv run dock-hub                     # 默认系统托盘
uv run dock-hub --no-tray           # 前台控制台，Ctrl+C 退出
uv run dock-hub --login             # 强制重新扫码登录米家（打开浏览器二维码）后退出
```

米家过期时：托盘点 **「重新登录米家」**，或运行 `--login`。用**米家 App**扫浏览器里的码；链接也在 `%USERPROFILE%\.config\dock-hub\mijia-login.txt`。

管理员无窗口（源码或 exe）：

```powershell
powershell -ExecutionPolicy Bypass -File .\run-admin.ps1
```

### 配置要点（`hub.yaml`）

- `token`：安卓 Bearer；`--init` 已生成
- `temperature`：只允许一个；没有就删整段
- `devices`：`light` / `switch` 用 `mijia_name`；`action` 用本机 `run.program`（路径不发给手机）
- `icon`：Remix 短名（如 `steam`、`cursor`、`computer`）；安卓据此画图标
- `pc.enabled: true`：snapshot 带 CPU / 内存；有 NVIDIA 再带 GPU；CPU 温度需管理员 + PawnIO

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
