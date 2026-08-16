"""No-console entry for Windows. Start with pythonw.exe, not the uv venv trampoline."""

from __future__ import annotations

import sys
import traceback
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / ".venv" / "Lib" / "site-packages"))
sys.path.insert(0, str(HERE))

log = Path.home() / ".config" / "dock-hub" / "hub.log"
log.parent.mkdir(parents=True, exist_ok=True)
if sys.stdout is None or sys.stderr is None:
    stream = log.open("a", encoding="utf-8", buffering=1)
    if sys.stdout is None:
        sys.stdout = stream
    if sys.stderr is None:
        sys.stderr = stream

try:
    from dock_hub.__main__ import main

    raise SystemExit(main())
except SystemExit:
    raise
except Exception:
    log = Path.home() / ".config" / "dock-hub" / "hub.log"
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open("a", encoding="utf-8") as fh:
        fh.write(traceback.format_exc())
    raise
