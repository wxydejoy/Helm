from __future__ import annotations

import plistlib
import unittest

from helm_mini.hid_temp import parse_die_temps
from helm_mini.sampler import MiniSampler, parse_gpu_plist


class ParseGpuTest(unittest.TestCase):
    def test_reads_device_utilization(self) -> None:
        raw = plistlib.dumps(
            [
                {
                    "model": "Apple M4",
                    "PerformanceStatistics": {
                        "Device Utilization %": 17.4,
                        "Renderer Utilization %": 4,
                    },
                }
            ]
        )
        gpu = parse_gpu_plist(raw)
        self.assertEqual(gpu, {"percent": 17.4, "name": "M4"})

    def test_bad_plist_returns_none(self) -> None:
        self.assertIsNone(parse_gpu_plist(b"not-a-plist"))


class DieTempTest(unittest.TestCase):
    def test_pmu_tdie_is_cpu_pmu2_is_gpu(self) -> None:
        cpu, gpu = parse_die_temps(
            [
                ("PMU tcal", 51.8),
                ("PMU tdie1", 31.4),
                ("PMU tdie8", 33.1),
                ("PMU2 tdie1", 28.2),
                ("PMU2 tdie3", 29.0),
                ("NAND CH0 temp", 27.0),
            ]
        )
        self.assertEqual(cpu, 33.1)
        self.assertEqual(gpu, 29.0)

    def test_unified_die_copies_cpu_to_gpu(self) -> None:
        cpu, gpu = parse_die_temps([("PMU tdie1", 40.2)])
        self.assertEqual(cpu, 40.2)
        self.assertEqual(gpu, 40.2)


class SamplerTest(unittest.TestCase):
    def test_offline_before_first_sample(self) -> None:
        sampler = MiniSampler(sample_ms=50_000)
        snap = sampler.snapshot()
        self.assertFalse(snap["online"])
        self.assertIn("updated_at", snap)


if __name__ == "__main__":
    unittest.main()
