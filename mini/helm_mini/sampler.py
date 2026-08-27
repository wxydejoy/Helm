from __future__ import annotations

import plistlib
import subprocess
import threading
from datetime import datetime, timezone
from typing import Any

from helm_mini.hid_temp import read_die_temps

try:
    import psutil
except ImportError:  # pragma: no cover
    psutil = None


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _round(value: float, digits: int = 1) -> float:
    return round(float(value), digits)


def parse_gpu_plist(raw: bytes) -> dict[str, Any] | None:
    try:
        items = plistlib.loads(raw)
    except Exception:
        return None
    if not isinstance(items, list):
        return None
    best: dict[str, Any] | None = None
    best_util = -1.0
    for item in items:
        if not isinstance(item, dict):
            continue
        stats = item.get("PerformanceStatistics")
        if not isinstance(stats, dict):
            continue
        util = stats.get("Device Utilization %")
        if util is None:
            util = stats.get("Renderer Utilization %")
        if util is None:
            continue
        try:
            percent = _round(float(util))
        except (TypeError, ValueError):
            continue
        name = str(item.get("model") or "Apple GPU").replace("Apple ", "").strip()
        gpu: dict[str, Any] = {"percent": percent}
        if name:
            gpu["name"] = name
        if percent >= best_util:
            best_util = percent
            best = gpu
    return best


class MiniSampler:
    def __init__(self, sample_ms: int = 2000) -> None:
        self.sample_ms = max(400, int(sample_ms))
        self._lock = threading.Lock()
        self._sample: dict[str, Any] | None = None
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._cpu_primed = False

    def start(self) -> None:
        if psutil is not None:
            psutil.cpu_percent(interval=None)
        self._thread = threading.Thread(target=self._loop, name="helm-mini-sampler", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            if self._sample is None:
                return {"online": False, "updated_at": utc_now()}
            return dict(self._sample)

    def _loop(self) -> None:
        interval = self.sample_ms / 1000.0
        while True:
            try:
                self._sample_once()
            except Exception:
                with self._lock:
                    self._sample = {"online": False, "updated_at": utc_now()}
            if self._stop.wait(interval):
                break

    def _sample_once(self) -> None:
        if psutil is None:
            sample = {"online": False, "updated_at": utc_now()}
            with self._lock:
                self._sample = sample
            return
        cpu: dict[str, Any] = {
            "percent": _round(
                psutil.cpu_percent(interval=0.12 if not self._cpu_primed else None),
            ),
        }
        self._cpu_primed = True
        cpu_temp, gpu_temp = read_die_temps()
        if cpu_temp is not None:
            cpu["temp_celsius"] = cpu_temp
        mem = psutil.virtual_memory()
        sample: dict[str, Any] = {
            "online": True,
            "updated_at": utc_now(),
            "cpu": cpu,
            "memory": {
                "percent": _round(mem.percent),
                "used_gb": _round(mem.used / (1024**3)),
                "total_gb": _round(mem.total / (1024**3)),
            },
        }
        gpu = _gpu_sample()
        if gpu:
            if gpu_temp is not None:
                gpu["temp_celsius"] = gpu_temp
            sample["gpu"] = gpu
        with self._lock:
            self._sample = sample


def _gpu_sample() -> dict[str, Any] | None:
    try:
        raw = subprocess.check_output(
            ["ioreg", "-r", "-d", "1", "-c", "IOAccelerator", "-a"],
            timeout=2.0,
        )
    except Exception:
        return None
    return parse_gpu_plist(raw)
