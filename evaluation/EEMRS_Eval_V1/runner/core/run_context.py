import hashlib
import json
import subprocess
from datetime import datetime
from pathlib import Path
from typing import Any, Dict


BASE = Path(__file__).resolve().parents[2]


def git_commit() -> str:
    try:
        return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=BASE.parents[1], text=True).strip()
    except Exception:
        return "UNKNOWN"


def load_manifest() -> Dict[str, Any]:
    path = BASE / "dataset_manifest.json"
    return json.loads(path.read_text(encoding="utf-8"))


def run_manifest(run_id: str, split: str, config: Dict[str, Any]) -> Dict[str, Any]:
    dataset_manifest = load_manifest()
    return {
        "run_id": run_id,
        "split": split.upper(),
        "dataset_version": dataset_manifest.get("dataset_version", "UNKNOWN"),
        "dataset_hash": hashlib.sha256(json.dumps(dataset_manifest.get("dataset_hashes", {}), sort_keys=True).encode()).hexdigest(),
        "dataset_freeze_revision": dataset_manifest.get("freeze_revision", "UNKNOWN"),
        "code_commit": git_commit(),
        "model_name": config.get("model_name", "UNKNOWN"),
        "model_version": config.get("model_version", "UNKNOWN"),
        "prompt_version": config.get("prompt_version", "UNKNOWN"),
        "KB_version": config.get("kb_version", "UNKNOWN"),
        "embedding_model": config.get("embedding_model", "UNKNOWN"),
        "retrieval_config": config.get("retrieval_config", "UNKNOWN"),
        "temperature": config.get("temperature", "UNKNOWN"),
        "max_tokens": config.get("max_tokens", "UNKNOWN"),
        "timeout_configuration": config.get("task_timeouts_seconds", {}),
        "trace_timeout_seconds": config.get("trace_timeout_seconds", "UNKNOWN"),
        "fixture_timeout_seconds": config.get("fixture_timeout_seconds", "UNKNOWN"),
        "retry_policy": "disabled_by_default",
        "run_started_at": datetime.now().isoformat(timespec="seconds"),
        "run_finished_at": None,
    }
