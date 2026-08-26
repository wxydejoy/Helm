#!/bin/sh
# 下载「岸宝」唤醒词所需的 sherpa-onnx AAR 与中文 KWS 模型。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
AAR_DIR="$ROOT/app/libs"
KWS_DIR="$ROOT/app/src/main/assets/kws"
mkdir -p "$AAR_DIR" "$KWS_DIR"

AAR="$AAR_DIR/sherpa-onnx-1.13.6.aar"
if [ ! -s "$AAR" ]; then
  curl -L --fail -o "$AAR" \
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.6/sherpa-onnx-1.13.6.aar"
fi

if [ -s "$KWS_DIR/encoder.int8.onnx" ] && [ -s "$KWS_DIR/decoder.onnx" ] && [ -s "$KWS_DIR/joiner.int8.onnx" ]; then
  echo "wake-word assets already present"
  exit 0
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
curl -L --fail -o "$TMP/kws-mobile.tar.bz2" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01-mobile.tar.bz2"
tar xjf "$TMP/kws-mobile.tar.bz2" -C "$TMP"
SRC="$TMP/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01-mobile"
cp "$SRC/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx" "$KWS_DIR/encoder.int8.onnx"
cp "$SRC/decoder-epoch-12-avg-2-chunk-16-left-64.onnx" "$KWS_DIR/decoder.onnx"
cp "$SRC/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx" "$KWS_DIR/joiner.int8.onnx"
cp "$SRC/tokens.txt" "$KWS_DIR/tokens.txt"
echo "wake-word models ready in $KWS_DIR"
