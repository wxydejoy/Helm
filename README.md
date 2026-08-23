# Helm（Dock）

横屏安卓桌面摆件 + Windows Python Hub：显示天气与室内温湿度、电脑监控，控制米家设备，一键启动电脑上的白名单程序。

![Helm 主屏](docs/screenshots/home.png)

手机只通过局域网 HTTP 与 Hub 通信，不直连米家。协议是两边的唯一契约：

- [docs/lan-protocol.md](docs/lan-protocol.md) — 说明、时序、验收
- [docs/openapi.yaml](docs/openapi.yaml) — OpenAPI 3

默认 `http://<电脑局域网IP>:17890`，共享 Bearer token。

## 仓库结构

| 目录 | 说明 |
|------|------|
| [android/](android/) | 安卓客户端（Kotlin / Jetpack Compose） |
| [hub/](hub/README.md) | Windows Hub（米家 + 本机启动 + PC 监控 + 托盘 exe） |
| [docs/](docs/) | 局域网协议（改接口先改文档） |

## 安卓（客户端）

横屏全屏 Dock，未配置 Hub 时进入**预览模式**（本地演示数据）。

### 主屏

- 大时钟 + 中文日期（多种字体与字号）
- **PC 监控条**：CPU / 内存 / GPU / FPS（有数据时约 3s 刷新）— 点击进入设置
- **左侧**：最多 3 个 Windows 应用快捷启动（Remix 图标）
- **右侧**：米家灯/开关 + **手机媒体控制**（上一首 / 播放暂停 / 下一首）
- **底部**：室外天气 + 室内温湿度
- 可选**视频背景**（循环静音）；方块样式（磨砂 / 深色 / 实心 / 细线）与透明度可调
- **主屏编排**：长按模块可拖动、缩放；可隐藏模块或去掉背景边框

### 设置

- Hub：**地址 / 端口 / Token**，测试连接（`/health`）
- 视频背景：选择 / 更换 / 清除本地视频
- 方块样式与字体排版预览
- **插电亮屏、拔电熄屏**（需设备管理员，仅用于 `lockNow()`）
- 媒体控制需开启**通知使用权**

### 构建

```bash
cd android
./gradlew :app:assembleDebug
```

安装后首次打开为预览；在设置页填入 Hub 的 IP、`17890`、token 即可联机。

单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

## Windows Hub（服务端）

完整说明见 [hub/README.md](hub/README.md)。

**推荐：打包 exe，管理员运行（托盘图标 + 米家重新登录）**

```powershell
cd hub
powershell -ExecutionPolicy Bypass -File .\build-exe.ps1
# 右键 dist\DockHub.exe → 以管理员身份运行
```

**源码开发：**

```powershell
cd hub
uv sync
uv run dock-hub --init
uv run dock-hub --list-devices
uv run dock-hub
uv run dock-hub --login    # 米家过期时重新扫码
```

配置：`%USERPROFILE%\.config\dock-hub\hub.yaml`（token、设备白名单、本机程序路径）。

防火墙放行入站 **TCP 17890**；CPU 温度需 **PawnIO** + 管理员权限。

## 联机速查

1. Windows 启动 Hub，记下局域网 IP（如 `10.83.22.31`）与 `hub.yaml` 里的 token
2. 安卓设置页填入 IP、端口 `17890`、token，点测试连接
3. 米家设备名在 Hub 侧用 `uv run dock-hub --list-devices` 核对

## 许可

- Hub 依赖 [mijia-api](https://github.com/Do1e/mijia-api)（GPL-3.0）；分发 Hub 需同样开源
- 安卓内置字体见 `android/app/src/main/assets/font-licenses/`（OFL）
