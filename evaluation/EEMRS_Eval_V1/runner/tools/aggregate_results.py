import json
import sys
from pathlib import Path


def main() -> None:
    path = Path(sys.argv[1])
    rows = [json.loads(line) for line in (path / "case_results.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
    print(json.dumps({"case_count": len(rows), "error_count": sum(1 for r in rows if r.get("error"))}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

