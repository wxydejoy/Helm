# 安卓客户端（岸亭）

横屏全屏摆件，包名 `cn.weiekko.dock`。当前 debug：**0.3.7**（`versionCode` 10）。

未配置 Hub 时是预览模式（本地演示数据）。联机后只通过局域网 HTTP 跟 **Windows Hub** 说话，不直连米家、Ollama、TTS。

协议：[docs/lan-protocol.md](../docs/lan-protocol.md)。守岸人：[docs/companion.md](../docs/companion.md)。

## 设置里两栏不要填反

| 栏 | 填谁 | 本宅默认 |
|---|---|---|
| **Hub** | Windows 门口 | `10.83.22.31` 端口 `17890` + Hub token |
| **Mac Mini** | 主屏 CPU 第二行 | `10.83.22.121` 端口 `17891` |

Hub 栏填 Mini 的 IP 时，测试连接会拒绝。Mini 上不要再开一份 `dock-hub:17890`。

## 构建

需要 JDK 17（Android Studio 自带 JBR 即可）和一次网络，用来拉唤醒词 / ASR 模型（不进 git）。

```bash
cd android
./fetch-wake-word.sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

APK：`android/app/build/outputs/apk/debug/app-debug.apk`。装到横屏设备（本宅是 K20，adb `10.83.22.150:5555`）。

首次打开为预览；设置页填 Hub IP、`17890`、token，点测试连接。

## 语音

- 唤醒词「岸宝」（sherpa-onnx zipformer KWS）
- 听完由 `UtteranceGate`：结束静音 1s、最短句 1.5s
- 字幕等 `POST /v1/companion/chat` 全文；声音由 Mini TTS 出，本机不播守岸人 wav
- 她正在说时再喊「岸宝」：`POST /v1/companion/stop` 后重新听
