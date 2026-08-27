# 守岸人 Companion — 协作大纲

只做**在家**：手机只连 Hub（局域网 IP + token）。不出门档、不直连 Ollama / TTS / 百炼。

改接口先改 [lan-protocol.md](./lan-protocol.md) 和 [openapi.yaml](./openapi.yaml)。

## 拓扑

```
K20 Helm  ──Bearer──►  Hub :17890（这台 Mini 或 Windows）
                         ├─ 米家 / 开程序 / pc（多在 Windows）
                         ├─ POST /v1/companion/chat  → Mini Ollama :11434  qwen3.5:4b
                         └─ TTS :18100（Hub 当客户端；安卓只 GET Hub 的 audio）
```

Hub 在 Mini 上时，`hub.yaml` 里 llm / tts 用 `127.0.0.1`。

## 安卓（在家）

- 设置：Hub 的 host / port / token。不要填 Ollama、TTS、百炼
- `companion != null && ready`：主屏可点人物（时钟区域）说话
- `null` 或 `ready == false`：不打开对话
- `POST /v1/companion/chat`，超时 30s
- 字幕显示 `text`；`audio_id` 非空时 `GET /v1/companion/audio/{id}` 播 wav
- 播放器用独立播放器，不要动循环视频的静音

Hub 在 Windows 时，`hub.yaml` 的 `companion.tts.base_url` 填 Mini：`http://10.83.22.121:18100`。TTS 进程在 Mini 上跑，不要打进 `dock_hub` 包。

## 人设

默认在 `hub/dock_hub/companion.py` 的 `DEFAULT_PERSONA`。慢、短、轻；1–3 句；可称「漂泊者」。
