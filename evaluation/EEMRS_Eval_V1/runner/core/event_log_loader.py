from __future__ import annotations

from pathlib import Path
from typing import Any, Dict, List

from core.human_review_loader import load_jsonl


def load_event_logs(root: Path) -> List[Dict[str, Any]]:
    event_root = root / "events"
    rows: List[Dict[str, Any]] = []
    for path in sorted(event_root.glob("*.jsonl")):
        if path.name.endswith(".template.jsonl"):
            continue
        rows.extend(load_jsonl(path))
    return rows
