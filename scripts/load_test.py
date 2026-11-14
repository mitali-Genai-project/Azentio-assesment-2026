#!/usr/bin/env python3
"""Small load test for POST /api/screenings (standard library only).

Sends N requests (default 1,000) where about half reuse a small pool of names (likely cache hits) and the rest
are unique names. Reports client-side p50/p95 latency and the server-reported cache hit ratio (the
`cacheHit` field of each screening response).

Cache-enabled vs cache-disabled is a *server* setting, so run the API once per mode and label the run:
    CACHE_TYPE=redis -> python scripts/load_test.py --label cache-enabled
    CACHE_TYPE=none  -> python scripts/load_test.py --label cache-disabled

Credentials come from CLEARLIST_ANALYST_PASSWORD (user "analyst"). A watchlist must already be loaded.
"""
from __future__ import annotations

import argparse
import base64
import csv
import json
import os
import random
import statistics
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


def build_names(count: int, repeat_ratio: float, seed: int, eval_file: Path) -> list[str]:
    rng = random.Random(seed)
    with open(eval_file, encoding="utf-8") as fh:
        base = [row["customerName"] for row in csv.DictReader(fh)]
    repeated_pool = base[:20]                         # small pool -> repeated names
    names = []
    for i in range(count):
        if rng.random() < repeat_ratio:
            names.append(rng.choice(repeated_pool))
        else:                                         # unique: extra letters-only token so it never repeats
            names.append(f"{rng.choice(base)} {letters(i)}")
    return names


def letters(n: int) -> str:
    """Letters-only unique token (digits would be stripped by the server's name normaliser)."""
    out = ""
    for _ in range(5):
        n, r = divmod(n, 26)
        out = chr(97 + r) + out
    return "X" + out


def percentile(sorted_values: list[float], pct: float) -> float:
    index = min(len(sorted_values) - 1, max(0, round(pct / 100 * len(sorted_values)) - 1))
    return sorted_values[index]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="http://localhost:8080/api/screenings")
    parser.add_argument("--requests", type=int, default=1000)
    parser.add_argument("--repeat-ratio", type=float, default=0.5)
    parser.add_argument("--concurrency", type=int, default=8)
    parser.add_argument("--label", default="run")
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--eval-file", type=Path, default=Path(__file__).resolve().parent.parent / "data" / "evaluation.csv")
    args = parser.parse_args()

    password = os.environ.get("CLEARLIST_ANALYST_PASSWORD")
    if not password:
        print("Set CLEARLIST_ANALYST_PASSWORD", file=sys.stderr)
        return 2
    auth = "Basic " + base64.b64encode(f"analyst:{password}".encode()).decode()
    names = build_names(args.requests, args.repeat_ratio, args.seed, args.eval_file)

    def call(i_name: tuple[int, str]) -> tuple[float, bool | None, int]:
        i, name = i_name
        body = json.dumps({"customerRef": f"LOAD-{i:05d}", "fullName": name}).encode()
        req = urllib.request.Request(args.url, data=body, method="POST",
                                     headers={"Content-Type": "application/json", "Authorization": auth})
        start = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                payload = json.loads(resp.read())
                return (time.perf_counter() - start) * 1000, payload.get("cacheHit"), resp.status
        except urllib.error.HTTPError as e:
            return (time.perf_counter() - start) * 1000, None, e.code

    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        results = list(pool.map(call, enumerate(names)))
    elapsed = time.perf_counter() - started

    ok = [r for r in results if r[2] == 201]
    latencies = sorted(r[0] for r in ok)
    if not latencies:
        print(f"All {len(results)} requests failed; first status: {results[0][2]}", file=sys.stderr)
        return 1
    hits = sum(1 for r in ok if r[1])
    print(f"label={args.label} requests={len(results)} ok={len(ok)} errors={len(results) - len(ok)} "
          f"concurrency={args.concurrency} duration_s={elapsed:.1f} throughput_rps={len(results) / elapsed:.1f}")
    print(f"p50_ms={percentile(latencies, 50):.1f} p95_ms={percentile(latencies, 95):.1f} "
          f"mean_ms={statistics.fmean(latencies):.1f} cache_hit_ratio={hits / len(ok):.3f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
