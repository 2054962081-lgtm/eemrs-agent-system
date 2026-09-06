import json
import sys
from collections import Counter
from pathlib import Path


def main() -> None:
    path = Path(sys.argv[1])
    rows = [json.loads(line) for line in (path / "bad_cases.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
    print(json.dumps({"bad_case_count": len(rows), "root_cause": Counter(r.get("root_cause", "UNKNOWN") for r in rows)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

