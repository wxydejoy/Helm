from __future__ import annotations

import ctypes
from ctypes import c_bool, c_double, c_int, c_int64, c_uint32, c_void_p
from typing import Sequence


def parse_die_temps(readings: Sequence[tuple[str, float]]) -> tuple[float | None, float | None]:
    """PMU tdie → CPU；PMU2 tdie → GPU 区域。跳过 tcal。统一封装则 GPU 跟 CPU。"""
    cpu: list[float] = []
    gpu: list[float] = []
    for name, raw in readings:
        try:
            value = float(raw)
        except (TypeError, ValueError):
            continue
        if not (1.0 < value < 115.0):
            continue
        key = name.strip().lower()
        if "tcal" in key or "nand" in key or "battery" in key:
            continue
        if "tdie" not in key:
            continue
        if key.startswith("pmu2"):
            gpu.append(value)
        elif key.startswith("pmu"):
            cpu.append(value)
    cpu_t = round(max(cpu), 1) if cpu else None
    gpu_t = round(max(gpu), 1) if gpu else cpu_t
    return cpu_t, gpu_t


_k_cf_utf8 = 0x08000100
_k_cf_number_double = 13
_k_hid_temp_type = 15
_k_hid_temp_field = _k_hid_temp_type << 16


class HidThermals:
    def __init__(self) -> None:
        self._ok = False
        self._client = None
        self._cf = None
        self._iokit = None
        try:
            self._cf = ctypes.cdll.LoadLibrary(
                "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation",
            )
            self._iokit = ctypes.cdll.LoadLibrary(
                "/System/Library/Frameworks/IOKit.framework/IOKit",
            )
            cf = self._cf
            iokit = self._iokit
            cf.CFStringCreateWithCString.restype = c_void_p
            cf.CFStringCreateWithCString.argtypes = [c_void_p, ctypes.c_char_p, c_uint32]
            cf.CFStringGetLength.restype = ctypes.c_long
            cf.CFStringGetLength.argtypes = [c_void_p]
            cf.CFStringGetCString.restype = c_bool
            cf.CFStringGetCString.argtypes = [c_void_p, ctypes.c_char_p, ctypes.c_long, c_uint32]
            cf.CFArrayGetCount.restype = ctypes.c_long
            cf.CFArrayGetCount.argtypes = [c_void_p]
            cf.CFArrayGetValueAtIndex.restype = c_void_p
            cf.CFArrayGetValueAtIndex.argtypes = [c_void_p, ctypes.c_long]
            cf.CFRelease.argtypes = [c_void_p]
            iokit.IOHIDEventSystemClientCreate.restype = c_void_p
            iokit.IOHIDEventSystemClientCreate.argtypes = [c_void_p]
            iokit.IOHIDEventSystemClientCopyServices.restype = c_void_p
            iokit.IOHIDEventSystemClientCopyServices.argtypes = [c_void_p]
            iokit.IOHIDServiceClientCopyProperty.restype = c_void_p
            iokit.IOHIDServiceClientCopyProperty.argtypes = [c_void_p, c_void_p]
            iokit.IOHIDServiceClientCopyEvent.restype = c_void_p
            iokit.IOHIDServiceClientCopyEvent.argtypes = [c_void_p, c_int, c_void_p, c_int64]
            iokit.IOHIDEventGetFloatValue.restype = c_double
            iokit.IOHIDEventGetFloatValue.argtypes = [c_void_p, c_int]
            self._client = iokit.IOHIDEventSystemClientCreate(None)
            self._product_key = cf.CFStringCreateWithCString(None, b"Product", _k_cf_utf8)
            self._ok = bool(self._client and self._product_key)
        except Exception:
            self._ok = False

    def read(self) -> tuple[float | None, float | None]:
        if not self._ok:
            return None, None
        cf = self._cf
        iokit = self._iokit
        try:
            services = iokit.IOHIDEventSystemClientCopyServices(self._client)
            if not services:
                return None, None
            readings: list[tuple[str, float]] = []
            count = cf.CFArrayGetCount(services)
            for index in range(count):
                service = cf.CFArrayGetValueAtIndex(services, index)
                event = iokit.IOHIDServiceClientCopyEvent(
                    service, _k_hid_temp_type, None, 0,
                )
                if not event:
                    continue
                value = float(iokit.IOHIDEventGetFloatValue(event, _k_hid_temp_field))
                cf.CFRelease(event)
                product_ref = iokit.IOHIDServiceClientCopyProperty(service, self._product_key)
                name = _cf_string(cf, product_ref) if product_ref else ""
                if product_ref:
                    cf.CFRelease(product_ref)
                readings.append((name, value))
            cf.CFRelease(services)
            return parse_die_temps(readings)
        except Exception:
            return None, None


def _cf_string(cf, ref: int) -> str:
    length = cf.CFStringGetLength(ref)
    buf = ctypes.create_string_buffer(max(8, length * 4 + 8))
    if cf.CFStringGetCString(ref, buf, len(buf), _k_cf_utf8):
        return buf.value.decode("utf-8", "replace")
    return ""


_shared: HidThermals | None = None


def read_die_temps() -> tuple[float | None, float | None]:
    global _shared
    if _shared is None:
        _shared = HidThermals()
    return _shared.read()
