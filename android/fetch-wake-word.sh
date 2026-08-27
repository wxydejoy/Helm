#!/bin/sh
# 下载「岸宝」唤醒词与中文流式识别模型。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
AAR_DIR="$ROOT/app/libs"
KWS_DIR="$ROOT/app/src/main/assets/kws"
ASR_DIR="$ROOT/app/src/main/assets/asr"
mkdir -p "$AAR_DIR" "$KWS_DIR" "$ASR_DIR"

AAR="$AAR_DIR/sherpa-onnx-1.13.6.aar"
if [ ! -s "$AAR" ]; then
  curl -L --fail -o "$AAR" \
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.6/sherpa-onnx-1.13.6.aar"
fi

if [ ! -s "$KWS_DIR/encoder.int8.onnx" ] || [ ! -s "$KWS_DIR/decoder.onnx" ] || [ ! -s "$KWS_DIR/joiner.int8.onnx" ]; then
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
  rm -rf "$TMP"
  trap - EXIT
fi

if [ ! -s "$ASR_DIR/model.int8.onnx" ]; then
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  curl -L --fail -o "$TMP/asr-ctc.tar.bz2" \
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01.tar.bz2"
  tar xjf "$TMP/asr-ctc.tar.bz2" -C "$TMP"
  SRC="$TMP/sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01"
  cp "$SRC/model.int8.onnx" "$ASR_DIR/model.int8.onnx"
  cp "$SRC/tokens.txt" "$ASR_DIR/tokens.txt"
fi

echo "voice models ready"
