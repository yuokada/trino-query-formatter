#!/usr/bin/env python3
"""Render repository-wide Java scc metrics as a PR comment."""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

COMMENT_MARKER = "<!-- scc-full-report -->"
DEFAULT_SCC_VERSION = "unknown"


def metric(row: dict, key: str) -> int:
  value = row.get(key, 0)
  return value if isinstance(value, int) else 0


def render(data: list[dict], commit_sha: str) -> str:
  files = sum(metric(row, "Count") for row in data)
  code = sum(metric(row, "Code") for row in data)
  comments = sum(metric(row, "Comment") for row in data)
  blanks = sum(metric(row, "Blank") for row in data)
  complexity = sum(metric(row, "Complexity") for row in data)
  lines = sum(metric(row, "Lines") for row in data)
  if lines == 0:
    lines = code + comments + blanks

  version = os.environ.get("SCC_VERSION", DEFAULT_SCC_VERSION)
  return "\n".join(
      [
          COMMENT_MARKER,
          f"## Repository-wide Java metrics (scc v{version})",
          "",
          f"Merged commit: `{commit_sha}`",
          "",
          "| Files | Lines | Code | Comments | Blanks | Complexity |",
          "|---:|---:|---:|---:|---:|---:|",
          f"| {files} | {lines} | {code} | {comments} | {blanks} | "
          f"{complexity} |",
          "",
      ]
  )


def main() -> int:
  if len(sys.argv) != 3:
    print("usage: scc_full_report.py SCC_JSON COMMIT_SHA", file=sys.stderr)
    return 2

  json_path = Path(sys.argv[1])
  try:
    data = json.loads(json_path.read_text())
  except (OSError, json.JSONDecodeError) as error:
    print(f"failed to read scc JSON: {error}", file=sys.stderr)
    return 1

  if not isinstance(data, list) or not all(isinstance(row, dict) for row in data):
    print("unexpected scc JSON structure", file=sys.stderr)
    return 1

  print(render(data, sys.argv[2]))
  return 0


if __name__ == "__main__":
  sys.exit(main())
