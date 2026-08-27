# Helm Mini

Mac Mini 上的本机监控：CPU / 内存 / GPU，给 Helm 主屏 CPU 块的第二行用。

手机默认连 `http://10.83.22.121:17891`，Token `helm-mini-weiekko`。

```bash
cd mini
uv sync
uv run helm-mini
```

探活：

```bash
curl -s http://127.0.0.1:17891/health
curl -s http://127.0.0.1:17891/v1/snapshot \
  -H "Authorization: Bearer helm-mini-weiekko"
```

登录项（当前用户，开机自启）：

```bash
bash ./install-launchd.sh
```

可选环境变量：`HELM_MINI_TOKEN`（默认 `helm-mini-weiekko`）、`HELM_MINI_PORT`（默认 17891）、`HELM_MINI_NAME`。
