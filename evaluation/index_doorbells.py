"""Index exact Doorbell annotations in local FSD50K ground truth (metadata only)."""
import argparse
import csv
import json
from collections import Counter
from pathlib import Path


def index(root):
    with (root / "vocabulary.csv").open(encoding="utf-8-sig", newline="") as stream:
        doorbell_mid, = [mid for _, label, mid in csv.reader(stream) if label == "Doorbell"]
    rows = []
    seen = set()
    for filename in ("dev.csv", "eval.csv"):
        with (root / filename).open(encoding="utf-8-sig", newline="") as stream:
            for record in csv.DictReader(stream):
                labels = record["labels"].split(",")
                mids = record["mids"].split(",")
                if ("Doorbell" in labels) != (doorbell_mid in mids):
                    raise ValueError("Doorbell label and ontology ID disagree")
                if doorbell_mid not in mids:
                    continue
                clip = record["fname"]
                split = "eval" if filename == "eval.csv" else record["split"]
                if clip in seen or split not in {"train", "val", "eval"}:
                    raise ValueError("Duplicate clip or unexpected split")
                seen.add(clip)
                rows.append({"clip_id": clip, "split": split, "labels": labels})
    counts = Counter(row["split"] for row in rows)
    return {"label": "Doorbell", "mid": doorbell_mid, "counts": dict(counts), "clips": rows}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ground_truth", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = index(args.ground_truth)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(result["counts"], "total:", len(result["clips"]))
