"""Small, fixed public doorbell replay; optional Ogg decoder: soundfile==0.13.1."""
import argparse
import json
import math
import zipfile
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

from audio_baseline import DEMAND, MODEL_SHA, digest, mix_at_snr, read_audio


# Original-file SHA-1 values published on each Wikimedia Commons file page.
SOURCES = {
    "Doorbell-classic-dingdong.ogg": "19a3463c37d4de915d58d54050b8d65d7445e63f",
    "Doorbell-cheap-dingdong.ogg": "735ac5e1e9240edee12a853d363c64d34c2a010f",
    "Doorbell-old-tring.ogg": "35c3fe8f970a20abfeb0acae43c5e36968a26511",
    "Electronic_doorbell.ogg": "435699e7886b531c5569739b31fe87b58bbd0cec",
}


def replay(model, audio_dir, living_zip):
    import tensorflow as tf

    if digest(model) != MODEL_SHA or digest(living_zip, "md5") != DEMAND["DLIVING"]:
        raise ValueError("Unexpected model or living-room noise archive")
    interpreter = tf.lite.Interpreter(model_path=str(model), num_threads=2)
    interpreter.allocate_tensors()
    inp, = interpreter.get_input_details()
    out, = interpreter.get_output_details()
    window = int(np.prod(inp["shape"]))
    with zipfile.ZipFile(model) as archive:
        labels = archive.read("yamnet_label_list.txt").decode().splitlines()
    doorbell = labels.index("Doorbell")
    with zipfile.ZipFile(living_zip) as archive:
        channel, = [n for n in archive.namelist() if n.lower().endswith("ch01.wav")]
        noise = read_audio(archive.read(channel))

    def infer(signal):
        if len(signal) < window:
            signal = np.pad(signal, (0, window - len(signal)))
        end = len(signal) - window
        starts = sorted(set(range(0, end + 1, 4000)) | {end})
        peaks = np.zeros(len(labels))
        for start in starts:
            interpreter.set_tensor(inp["index"], signal[start:start+window].reshape(inp["shape"]))
            interpreter.invoke()
            peaks = np.maximum(peaks, interpreter.get_tensor(out["index"]).ravel())
        top = int(np.argmax(peaks))
        return {"doorbell_peak": float(peaks[doorbell]), "crossed": bool(peaks[doorbell] >= .60),
                "top_peak_label": labels[top], "top_peak": float(peaks[top]), "windows": len(starts)}

    rows = []
    for filename, expected in SOURCES.items():
        path = audio_dir / filename
        if digest(path, "sha1") != expected:
            raise ValueError(f"Unexpected recording: {filename}")
        channels, rate = sf.read(path, dtype="float32", always_2d=True)
        factor = math.gcd(rate, 16000)
        signal = np.clip(resample_poly(channels.mean(axis=1), 16000//factor, rate//factor), -1, 1)
        offset = int(expected[:8], 16) % (len(noise) - len(signal) + 1)
        segment = noise[offset:offset + len(signal)]
        conditions = {"original": infer(signal), "background": infer(segment)}
        for snr in (20, 10, 0):
            mixture, _ = mix_at_snr(signal, segment, snr)
            conditions[f"{snr}dB"] = infer(mixture)
        rows.append({"file": filename, "sha1": expected, "rate": rate, "channels": channels.shape[1],
                     "noise_offset": offset, "conditions": conditions})
    return {"model_sha256": MODEL_SHA, "threshold": .60, "window": window, "hop": 4000,
            "sample_rate": 16000, "tensorflow": tf.__version__, "decoder": sf.__version__, "rows": rows}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ("model", "audio_dir", "living_zip", "output"):
        parser.add_argument(arg, type=Path)
    args = parser.parse_args()
    result = replay(args.model, args.audio_dir, args.living_zip)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    for row in result["rows"]:
        print(row["file"], {key: round(val["doorbell_peak"], 4) for key, val in row["conditions"].items()})
