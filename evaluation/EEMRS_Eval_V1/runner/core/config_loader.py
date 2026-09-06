import copy
import json
import os
from pathlib import Path
from typing import Any, Dict


DEFAULT_TASK_TIMEOUTS = {
    "report": 60,
    "consultation": 60,
    "safety": 60,
    "doctor_draft": 60,
}


def load_config(path: Path) -> Dict[str, Any]:
    config = json.loads(path.read_text(encoding="utf-8"))
    merged = copy.deepcopy(config)
    merged["task_timeouts_seconds"] = {
        **DEFAULT_TASK_TIMEOUTS,
        **config.get("task_timeouts_seconds", {}),
    }
    _apply_env_override(merged)
    return merged


def task_timeout(config: Dict[str, Any], dataset: str) -> int:
    return int(config.get("task_timeouts_seconds", {}).get(dataset, config.get("timeout_seconds", 30)))


def _apply_env_override(config: Dict[str, Any]) -> None:
    scalar_map = {
        "EEMRS_EVAL_AGENT_BASE_URL": "agent_base_url",
        "EEMRS_EVAL_TRACE_BASE_URL": "trace_base_url",
        "EEMRS_EVAL_RAG_BASE_URL": "rag_base_url",
        "EEMRS_EVAL_AGENT_MODE": "agent_mode",
    }
    for env_name, key in scalar_map.items():
        if os.getenv(env_name):
            config[key] = os.environ[env_name]

    int_map = {
        "EEMRS_EVAL_TRACE_TIMEOUT_SECONDS": "trace_timeout_seconds",
        "EEMRS_EVAL_FIXTURE_TIMEOUT_SECONDS": "fixture_timeout_seconds",
    }
    for env_name, key in int_map.items():
        if os.getenv(env_name):
            config[key] = int(os.environ[env_name])

    task_env = {
        "EEMRS_EVAL_TIMEOUT_REPORT": "report",
        "EEMRS_EVAL_TIMEOUT_CONSULTATION": "consultation",
        "EEMRS_EVAL_TIMEOUT_SAFETY": "safety",
        "EEMRS_EVAL_TIMEOUT_DOCTOR_DRAFT": "doctor_draft",
    }
    timeouts = config.setdefault("task_timeouts_seconds", {})
    for env_name, dataset in task_env.items():
        if os.getenv(env_name):
            timeouts[dataset] = int(os.environ[env_name])
