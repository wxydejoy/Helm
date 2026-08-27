# Mini TTS（守岸人）

独立进程，不要 `import dock_hub`。Hub 当客户端：`POST /v1/speak`、`POST /v1/stop`。安卓不直连本服务。

默认：Qwen3-TTS 12Hz 0.6B Base 6bit（MLX）+ 官方包片段克隆（`voices/shorekeeper/clone.wav`）。

Mac Mini 没有内置喇叭，`--play` 需要耳机或外接音箱。

## 启动

```bash
cd companion/tts
uv sync   # 或已有 .venv
export HF_HOME=.cache-base
export HF_HUB_OFFLINE=1
export HF_HUB_DISABLE_XET=1
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY all_proxy
.venv/bin/python server.py --host 0.0.0.0 --port 18100 \
  --ref-audio voices/shorekeeper/clone.wav --voice shorekeeper --play
```

探活：`curl -s http://127.0.0.1:18100/health`（`ready`、`play`、`clone`）。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/health` | 无鉴权 |
| `POST` | `/v1/speak` | JSON：`text`、`voice`、`language`、`turn_id`；`play_only: true` 时 **202**，本机播 |
| `POST` | `/v1/stop` | 立刻停播并中断这一轮合成 |

Hub 默认 `tts.deliver: false`：只 cue 本机播放，不把 wav 交给手机。
