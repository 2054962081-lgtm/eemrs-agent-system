import json
from pathlib import Path
from typing import Dict, Iterable, List, Tuple


BASE = Path(__file__).resolve().parents[2]
DATASETS = BASE / "datasets"


FILES = {
    ("consultation", "dev"): "consultation_dev.jsonl",
    ("consultation", "holdout"): "consultation_holdout.jsonl",
    ("safety", "dev"): "safety_dev.jsonl",
    ("safety", "holdout"): "safety_holdout.jsonl",
    ("report", "dev"): "report_dev.jsonl",
    ("report", "holdout"): "report_holdout.jsonl",
    ("doctor_draft", "dev"): "doctor_draft_dev.jsonl",
    ("doctor_draft", "holdout"): "doctor_draft_holdout.jsonl",
}


def read_jsonl(path: Path) -> List[Dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


class DatasetLoader:
    def __init__(self, dataset_dir: Path = DATASETS):
        self.dataset_dir = dataset_dir

    def load(self, split: str = "dev", datasets: Iterable[str] | None = None) -> List[Tuple[str, Dict]]:
        split = split.lower()
        if split not in {"dev", "holdout", "regression"}:
            raise ValueError("split must be dev, holdout, or regression")
        if split == "regression":
            return []
        selected = list(datasets or ["consultation", "safety", "report", "doctor_draft"])
        rows: List[Tuple[str, Dict]] = []
        for dataset in selected:
            path = self.dataset_dir / FILES[(dataset, split)]
            rows.extend((dataset, row) for row in read_jsonl(path))
        return rows

