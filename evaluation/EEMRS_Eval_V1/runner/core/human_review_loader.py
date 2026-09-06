from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, List


def load_jsonl(path: Path) -> List[Dict[str, Any]]:
    if not path.exists():
        return []
    rows: List[Dict[str, Any]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        row = json.loads(line)
        if not isinstance(row, dict):
            raise ValueError(f"{path}:{line_number} must contain one JSON object per line")
        rows.append(row)
    return rows


def load_human_reviews(root: Path) -> Dict[str, List[Dict[str, Any]]]:
    review_root = root / "human_review"
    return {
        "doctor_draft": load_jsonl(review_root / "doctor_draft_reviews.jsonl"),
        "report": load_jsonl(review_root / "report_reviews.jsonl"),
        "consultation": load_jsonl(review_root / "consultation_reviews.jsonl"),
    }
