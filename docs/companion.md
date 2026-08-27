# 守岸人 Companion — 协作大纲

只做**在家**：手机只连 Hub（局域网 IP + token）。不出门档、不直连 Ollama / TTS / 百炼。

改接口先改 [lan-protocol.md](./lan-protocol.md) 和 [openapi.yaml](./openapi.yaml)。

## 拓扑（三台，各干一件事）

```
K20 Helm（脸+耳+字幕）
   Bearer ──►  Windows Hub :17890（门口）
                  ├─ 米家 / 开程序 / pc / 媒体
                  ├─ chat → Mini Ollama :11434
                  └─ cue  → Mini TTS :18100（本机喇叭；不把 wav 回给手机）
                  └─ stop → Mini TTS /v1/stop（再喊「岸宝」打断）
```

| 谁 | 地址 | 干什么 | 不干什么 |
|---|---|---|---|
| **K20 安卓** | `10.83.22.150` | 脸：主屏、点灯、开程序、听你说话、显示字幕 | 不播守岸人声音；不直连 Ollama / TTS |
| **Windows Hub** | **`10.83.22.31:17890`** `service=dock-hub` `name=study` | 门口：米家、Steam 等、电脑监控；把对话转给 Mini | 不跑模型、不合成语音 |
| **Mac Mini 监控** | `10.83.22.121:17891` `service=helm-mini` | 主屏 CPU 块第二行 | 不是 Hub |
| **Mac Mini 脑+嘴** | Ollama `:11434`、TTS **`:18100 --play`** | 写字、本机出声 | 不当 Hub；安卓不连它 |

**安卓设置不要填反：** Hub 是 Windows `10.83.22.31:17890`，Mini 监控是 `10.83.22.121:17891`。Mini 上如果还开着一份 dock-hub `:17890`（`name=mini`），填错 IP 时健康检查会误报成功，真正拉米家/对话会失败。

Hub 若改到 Mini 上跑，`hub.yaml` 里 llm / tts 才用 `127.0.0.1`。现在 Hub 在 Windows。

## 安卓（在家）

- 设置：Hub 的 host / port / token。不要填 Ollama、TTS、百炼
- Hub 占位默认 `10.83.22.31`；粘贴 `IP:端口` 时会丢掉端口，避免拼成双端口
- 若 Hub 栏填了 Mini 的 IP，测试连接会直接拒绝
- `companion != null && ready`：主屏可点人物（时钟区域）说话；说「岸宝」同样开听
- `null` 或 `ready == false`：不打开对话
- `POST /v1/companion/chat`，超时 30s；可带 `turn_id`。默认 `tts.deliver: false`：Hub **边写边 cue** Mini TTS，HTTP 仍等全文再 `200` 给字幕
- 她正在说话时再喊「岸宝」：立刻 `POST /v1/companion/stop`，Mini 喇叭停，旧轮不再开口，然后重新听
- 字幕显示 `text`。声音从 Mini 喇叭出，安卓不要播 wav
- 只有 `companion.tts.deliver: true` 时才整段合成、chat 才带 `audio_id`，手机才去拉音频

Hub 在 Windows 时，`hub.yaml` 的 `companion.llm.base_url` / `companion.tts.base_url` 填 Mini：`http://10.83.22.121:11434` 与 `http://10.83.22.121:18100`。TTS 进程在 Mini 上跑（`--play`），不要打进 `dock_hub` 包。

## Mini TTS

目录：`companion/tts/`。独立进程，见 [../companion/tts/README.md](../companion/tts/README.md)。

```bash
cd companion/tts
HF_HOME=.cache-base HF_HUB_OFFLINE=1 HF_HUB_DISABLE_XET=1 \
  .venv/bin/python server.py --host 0.0.0.0 --port 18100 \
  --ref-audio voices/shorekeeper/clone.wav --voice shorekeeper --play
```

- `POST /v1/speak` + `play_only: true` → **202**，本机边合成边播
- `POST /v1/stop` → 立刻清播放缓冲并丢掉这一轮合成
- 安卓不要直连 `:18100`

## 一轮说话（耗时）

典型：喊「岸宝」→ **Mini 开口约 4～5s**（以前约 7～13s），字幕稍晚（等全文）。

听完由 `UtteranceGate` 决定：结束静音 **1s**（原 2.4s）、最短句 **1.5s**（原 3s），大约能省 1.5～2s。不要再往下压：短句和停顿会被切掉。

开口走 Hub `stream: true`：Ollama 每写出一句（`。！？` 或换行）就 `POST Mini /v1/speak` `play_only`。第一句句号后约 0.5s Mini 就能出声，不必等整段。`tts.deliver: true` 时仍整段合成，不边写边念。

| 阶段 | 大约 | 说明 |
|---|---:|---|
| 切 ASR | 0.5s | 跳过提示音 |
| 听完一句 | ~2.5s | 识别约 0.7s 出字；静音 1s + 最短 1.5s |
| 第一句开口 | 大脑写出第一句后约 0.5s | 按。！？切句 cue TTS |
| 字幕 | 全文写完 | chat HTTP 仍等 Ollama 结束 |

再喊「岸宝」走 stop，不走完整这一轮。

## 人设

默认在 `hub/dock_hub/companion.py` 的 `DEFAULT_PERSONA`。慢、短、轻；1–3 句；可称「漂泊者」。
