# 守岸人 Companion

桌上的小人：K20 听你说话并出字幕，Windows Hub 当门口，Mac Mini 写字并在本机喇叭出声。

只做**在家**。手机只连 Hub（局域网 IP + token），不直连 Ollama / TTS / 米家 / 百炼。

改 HTTP 接口先改 [lan-protocol.md](./lan-protocol.md) 和 [openapi.yaml](./openapi.yaml)。空闲卸模型见 [companion-power.md](./companion-power.md)。

## 三台机器

下面 IP 是**本宅当前地址**。换网络请改成自己的，安卓默认值也在 `HubPreferences` / `MiniConnection` 里。

```
K20 岸亭（脸+耳+字幕）
   Bearer ──►  Windows Hub :17890（门口）
                  ├─ 米家 / 开程序 / pc / 媒体
                  ├─ chat → Mini Ollama :11434
                  └─ cue  → Mini TTS :18100（本机喇叭；不把 wav 回给手机）
                  └─ stop → Mini TTS /v1/stop（再喊「岸宝」打断）
```

| 谁 | 地址 | 干什么 | 不干什么 |
|---|---|---|---|
| **K20 安卓** | `10.83.22.150`，包名 `cn.weiekko.dock` | 主屏、点灯、开程序、听你说话、显示字幕 | 不播守岸人声音；不直连 Ollama / TTS |
| **Windows Hub** | **`10.83.22.31:17890`** `service=dock-hub` `name=study` | 米家、Steam 等、电脑监控；把对话转给 Mini | 不跑模型、不合成语音 |
| **Mac Mini 监控** | `10.83.22.121:17891` `service=helm-mini` | 主屏 CPU 块第二行 | **不是 Hub** |
| **Mac Mini 脑+嘴** | Ollama `:11434`、TTS **`:18100 --play`** | 写字、本机出声 | 不当 Hub；安卓不连它 |

**安卓设置不要填反：** Hub 栏填 Windows `10.83.22.31`，Mini 监控填 `10.83.22.121:17891`。Mini 上如果还开着一份 dock-hub `:17890`（`name=mini`），Hub 栏填错 IP 时健康检查会误报成功，真正拉米家 / 对话会失败。安卓若检测到 Hub 栏是 Mini 的 IP，测试连接会直接拒绝。

Hub 若改到 Mini 上跑，`hub.yaml` 里 llm / tts 才用 `127.0.0.1`。现在 Hub 在 Windows。

## 启动顺序（本宅）

1. **Windows**：管理员运行 `hub/dist/DockHub.exe`（计划任务名 `HelmDockHub`）。探活：`curl -s http://10.83.22.31:17890/health` 应为 `service=dock-hub`、`name=study`。
2. **Mini 大脑**：`ollama serve`，模型 `qwen3.5:4b`。Hub 的 `companion.llm.base_url` 填 `http://10.83.22.121:11434`。
3. **Mini 嘴**：见下方 TTS。Mac Mini 没有内置喇叭，要接耳机或音箱。
4. **Mini 监控**（可选，主屏第二行 CPU）：`mini/` 的 `helm-mini`，`:17891`。
5. **K20**：设置里 Hub 填 Windows IP / `17890` / token；Mini 填 `10.83.22.121:17891`。主屏 `companion.ready` 后可点人物或喊「岸宝」。

改 `hub/dock_hub/companion.py` 之后必须在 Windows **重新打 exe** 再启动。正在跑的是 `DockHub.exe`，只拷 `.py` 不会生效。

## 安卓

当前 debug 包 **versionName `0.3.7`**（`versionCode` 10）。

- 设置只填 Hub 的 host / port / token。不要填 Ollama、TTS、百炼
- Hub 占位默认 `10.83.22.31`；粘贴 `IP:端口` 时会丢掉端口，避免拼成双端口
- `companion != null && ready`：主屏可点人物（时钟区域）说话；说「岸宝」同样开听
- `null` 或 `ready == false`：不打开对话。`ready` 只表示 Ollama 端口活着，权重卸了也仍可为 true
- `POST /v1/companion/chat`，超时 30s；可带 `turn_id`
- 默认 `tts.deliver: false`：Hub **边写边 cue** Mini TTS，HTTP 仍等全文再 `200` 给字幕
- 她正在说话时再喊「岸宝」：立刻 `POST /v1/companion/stop`，Mini 喇叭停，旧轮不再开口，然后重新听
- 字幕显示 `text`。声音从 Mini 喇叭出，安卓不要播 wav
- 只有 `companion.tts.deliver: true` 时才整段合成、chat 才带 `audio_id`，手机才去拉音频

听完由 `UtteranceGate` 决定，不是 sherpa 的 CTC 端点：

| 参数 | 当前 | 说明 |
|---|---:|---|
| 结束静音 | 1s | 太短会把停顿切成两句 |
| 最短句 | 1.5s | 太短会把「嗯」直接送走 |
| 空听超时 | 6.5s | 一直没出字 |
| 上限 | 16s | 再说也截 |

Hub 在 Windows 时，`hub.yaml` 的 `companion.llm.base_url` / `companion.tts.base_url` 填 Mini：`http://10.83.22.121:11434` 与 `http://10.83.22.121:18100`。TTS 进程在 Mini 上跑，不要打进 `dock_hub` 包。

## Mini TTS

独立进程，见 [../companion/tts/README.md](../companion/tts/README.md)。

```bash
cd companion/tts
HF_HOME=.cache-base HF_HUB_OFFLINE=1 HF_HUB_DISABLE_XET=1 \
  .venv/bin/python server.py --host 0.0.0.0 --port 18100 \
  --ref-audio voices/shorekeeper/clone.wav --voice shorekeeper --play
```

- `POST /v1/speak` + `play_only: true` → **202**，本机边合成边播
- 同一轮可以多次 speak（边写边念）：音频接到**同一条播放队列**，下一句不要 `flush` 上一句
- `POST /v1/stop` → 立刻清缓冲并丢掉这一轮合成（再喊「岸宝」走这条）
- 安卓不要直连 `:18100`

## 一轮说话

```
喊「岸宝」
  → K20 切 ASR，听完一句（约 2.5s）
  → POST Hub /v1/companion/chat
  → Hub 先 /v1/stop 清上一轮
  → Ollama stream:true，按 。！？ 切句
  → 每句 POST Mini /v1/speak play_only（第一句就能开口）
  → chat HTTP 等全文 200，K20 出字幕
```

典型：喊「岸宝」→ **Mini 开口约 4～5s**，字幕稍晚（等全文）。

| 阶段 | 大约 | 说明 |
|---|---:|---|
| 切 ASR | 0.5s | 跳过提示音 |
| 听完一句 | ~2.5s | 识别约 0.7s 出字；静音 1s + 最短 1.5s |
| 第一句开口 | 大脑写出第一句后约 0.5s | 按。！？切句 cue TTS |
| 字幕 | 全文写完 | chat HTTP 仍等 Ollama 结束 |

`tts.deliver: true` 时仍整段合成，不边写边念，并把 wav 交给手机。本宅默认 false。

再喊「岸宝」走 stop，不走完整这一轮。Hub 用序号丢掉旧轮还没 cue 的句子。

## 人设与「台灯开场」

默认人设在 `hub/dock_hub/companion.py` 的 `DEFAULT_PERSONA`：慢、短、轻；1–3 句；可称「漂泊者」；不要声称已经开灯。

温度、灯、电脑占用由 Hub 从 snapshot 抽成「后台状态」，放进 **system**，不要拼进用户这句话。4B 若看见用户消息以「当前状态：设备：台灯开」开头，打招呼也会从台灯起句。

闲聊不要报灯。只有对方问灯、温度、电脑时才用这些数字。

## Hub 配置要点

`%USERPROFILE%\.config\dock-hub\hub.yaml`（不要把 token 提交进仓库）：

```yaml
companion:
  enabled: true
  llm:
    base_url: "http://10.83.22.121:11434"
    model: qwen3.5:4b
    timeout_sec: 30
  tts:
    base_url: "http://10.83.22.121:18100"
    timeout_sec: 20
    # deliver: true   # 才把 wav 交给手机。默认 false
```

请求里 `think: false`。Ollama 用 Mini 的 `:11434`，不要填 Windows 的 127.0.0.1。

## 常见问题

| 现象 | 原因 | 怎么办 |
|---|---|---|
| 测试 Hub 成功，灯和对话失败 | Hub 栏填了 Mini 的 IP，连到 Mini 上另一份 dock-hub | 改成 Windows `10.83.22.31`；不要在 Mini 上再开 `:17890` 的 dock-hub |
| 有字幕没声音 | TTS 没 `--play`、没接音箱、或 Hub 没填 `tts.base_url` | Mini 上看 `/health` 的 `play` / `ready` |
| 说话一顿一顿 | 旧 TTS 在下一句合成时 `flush` 了上一句 | 用现在的 `server.py`：同轮只排队，只有 `/v1/stop` 才清空 |
| 每句都从「台灯」开头 | 状态被拼进了用户消息 | 已改为 system「后台状态」；需重打 Windows exe |
| 改了 companion.py 没变化 | 跑的是 exe | `build-exe.ps1` 后重启 `HelmDockHub` |
| TTS 502 `no attribute '_gate'` | 旧进程 | 停掉 `:18100` 再按上面的命令启动 |
| 开口很慢 | 听完静音仍偏长，或 Ollama 整段才 cue | 确认安卓 ≥ 0.3.7，Hub 走 `stream: true` |

## 不要做的

- 安卓直连 `:11434` / `:18100`
- 把 Hub token、米家 `auth.json` 写进文档或提交进 git
- 用 DeepSeek Harness 替换 Hub 当门口（技能若要接，也是 Hub 转发任务，脸和嘴仍走现在这条）
