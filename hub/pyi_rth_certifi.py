"""PyInstaller runtime hook: fix requests/urllib3 CA bundle in one-file exe."""

from __future__ import annotations

import os
import sys


def _apply() -> None:
    if not getattr(sys, "frozen", False):
        return
    base = getattr(sys, "_MEIPASS", "")
    candidates: list[str] = []
    if base:
        candidates.append(os.path.join(base, "certifi", "cacert.pem"))
    try:
        import certifi

        candidates.append(certifi.where())
    except Exception:
        pass
    for path in candidates:
        if path and os.path.isfile(path):
            os.environ["SSL_CERT_FILE"] = path
            os.environ["REQUESTS_CA_BUNDLE"] = path
            return


_apply()
