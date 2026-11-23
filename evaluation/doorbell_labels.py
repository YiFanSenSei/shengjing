"""Compare fixed doorbell label unions on four recordings and labelled distractors."""
import argparse
import csv
import io
import json
from collections import Counter
from pathlib import Path
from zipfile import ZipFile

from audio_baseline import ESC_SHA, MODEL_SHA, digest, read_audio
from doorbell_probe import SOURCES, load_ogg, make_inference

PHONE_FILE = "WWS_AutomatictelephoneIskraEta80ringing.ogg"
PHONE_SHA = "e4c58939fe1f4554e5413c9991f8e6eade334d8146023414af34dfb0558c7ae8"
DISTRACTORS = {"clock_alarm", "clock_tick", "church_bells", "door_wood_knock",
               "door_wood_creaks", "siren", "car_horn"}
RULES = {"baseline": ["Doorbell"], "plus_dingdong": ["Doorbell", "Ding-dong"],
         "plus_chime": ["Doorbell", "Ding-dong", "Chime"]}


def run(model, downloads):
    esc = downloads / "ESC-50-33c8ce9.zip"
    phone = downloads / PHONE_FILE
    if digest(esc) != ESC_SHA or digest(phone) != PHONE_SHA:
        raise ValueError("Unexpected distractor recording or ESC archive")
    labels, window, score = make_inference(model)
    monitored = ["Doorbell", "Ding-dong", "Chime", "Telephone", "Telephone bell ringing", "Music"]
    indices = {label: labels.index(label) for label in monitored}

    def measure(signal):
        peaks, _ = score(signal)
        return {label: float(peaks[index]) for label, index in indices.items()}

    positives = {}
    for name, sha in SOURCES.items():
        path = downloads / "commons-doorbells" / name
        if digest(path, "sha1") != sha:
            raise ValueError(f"Unexpected doorbell recording: {name}")
        positives[name] = measure(load_ogg(path)[0])
    phone_scores = measure(load_ogg(phone)[0])
    negatives = [("telephone_ringing", PHONE_FILE, phone_scores)]
    with ZipFile(esc) as archive:
        meta, = [name for name in archive.namelist() if name.endswith("/meta/esc50.csv")]
        prefix = meta.removesuffix("meta/esc50.csv")
        for row in csv.DictReader(io.StringIO(archive.read(meta).decode())):
            if row["category"] in DISTRACTORS:
                signal = read_audio(archive.read(prefix + "audio/" + row["filename"]))
                negatives.append((row["category"], row["filename"], measure(signal)))
    counts = Counter(category for category, _, _ in negatives)
    if any(counts[key] != 40 for key in DISTRACTORS):
        raise ValueError("Incomplete ESC distractor subset")
    comparisons = {}
    # 0.25 is a post-hoc diagnostic after inspecting the missed bells (~0.26).
    # Keep the fixed-threshold comparison separate from this development sweep.
    cases = [(name, keys, .60) for name, keys in RULES.items()]
    cases += [(name + "_exploratory025", keys, .25) for name, keys in RULES.items()]
    for rule, keys, threshold in cases:
        def triggers(scores):
            return max(scores[key] for key in keys) >= threshold

        comparisons[rule] = {
            "labels": keys,
            "threshold": threshold,
            "positive_hits": [name for name, scores in positives.items() if triggers(scores)],
            "negative_triggers": [{"category": category, "file": name} for category, name, scores
                                  in negatives if triggers(scores)],
            "max_negative_score": max(max(scores[key] for key in keys) for _, _, scores in negatives),
        }
    return {"model_sha256": MODEL_SHA, "window": window, "hop": 4000,
            "positive_peaks": positives, "telephone_peaks": phone_scores,
            "negative_counts": counts, "rules": comparisons}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ("model", "downloads", "output"):
        parser.add_argument(arg, type=Path)
    args = parser.parse_args()
    result = run(args.model, args.downloads)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    for name, rule in result["rules"].items():
        print(name, len(rule["positive_hits"]), len(rule["negative_triggers"]), flush=True)
