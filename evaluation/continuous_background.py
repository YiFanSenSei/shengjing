"""Replay uninterrupted DEMAND recordings through the phone model and production Java policy."""
import argparse
import json
import subprocess
import tempfile
import zipfile
from pathlib import Path

import numpy as np
from doorbell_probe import SOURCES, load_ogg
from audio_baseline import DEMAND, MODEL_SHA, digest, read_audio

ROOT = Path(__file__).resolve().parents[1]
LABELS = ['Knock', 'Doorbell', 'Alarm clock']


def run(downloads, model):
    import tensorflow as tf
    if digest(model) != MODEL_SHA:
        raise ValueError('Model checksum mismatch')
    with zipfile.ZipFile(model) as archive:
        labels = archive.read(next(p for p in archive.namelist() if p.endswith('.txt'))).decode().splitlines()
    indices = [labels.index(label) for label in LABELS]
    engine = tf.lite.Interpreter(model_path=str(model), num_threads=2)
    engine.allocate_tensors()
    inp = engine.get_input_details()[0]
    out = engine.get_output_details()[0]
    def replay_audio(audio, classes):
        rows = []
        # Phone fills four 4000-sample chunks before its first result, then takes the latest 15600.
        for end in range(16000, len(audio) + 1, 4000):
            engine.set_tensor(inp['index'], audio[end-15600:end].reshape(inp['shape']))
            engine.invoke()
            scores = engine.get_tensor(out['index']).reshape(-1)[indices]
            rows.append('\t'.join([str(end // 16), *(str(float(value)) for value in scores)]))
        track = {'duration_s': len(audio)/16000, 'windows': len(rows), 'alerts': {}}
        for scene in ('HOME', 'STUDY'):
            for enhanced in (False, True):
                replay = subprocess.run(['java', '-cp', classes, 'ContinuousReplay', scene, str(enhanced).lower()],
                                        input='\n'.join(rows), capture_output=True, text=True, check=True)
                events = []
                for line in replay.stdout.splitlines():
                    time, kind, score = line.split('\t')
                    events.append({'at_s': int(time)/1000, 'kind': kind, 'score': round(float(score), 5)})
                track['alerts'][scene + ('_enhanced' if enhanced else '_standard')] = events
        return track

    report = {'model_sha256': MODEL_SHA, 'tensorflow': tf.__version__, 'tracks': {}}
    with tempfile.TemporaryDirectory() as classes:
        subprocess.run(['javac', '-encoding', 'UTF-8', '-d', classes,
                        ROOT/'agent/engine/src/main/java/org/shengjing/core/Policy.java',
                        ROOT/'evaluation/ContinuousReplay.java'], check=True)
        for name, checksum in DEMAND.items():
            path = downloads / (name + '_16k.zip')
            if digest(path, 'md5') != checksum:
                raise ValueError('Unexpected archive: ' + name)
            with zipfile.ZipFile(path) as archive:
                audio = read_audio(archive.read(next(p for p in archive.namelist() if p.endswith('ch01.wav'))))
            track = replay_audio(audio, classes)
            track['archive_md5'] = checksum
            mixed = audio[:120*16000].copy()
            references = []
            for onset, (filename, expected) in zip((10, 40, 70, 100), SOURCES.items(), strict=True):
                source = downloads/'commons-doorbells'/filename
                if digest(source,'sha1') != expected:
                    raise ValueError('Doorbell checksum mismatch')
                signal, _, _ = load_ogg(source)
                start = onset * 16000
                segment = mixed[start:start+len(signal)]
                gain = np.linalg.norm(segment.astype(float)) / np.linalg.norm(signal.astype(float)) * 10**0.5
                segment += gain * signal
                references.append({'file': filename, 'onset_s': onset, 'offset_s': onset+len(signal)/16000})
            mixed /= max(1., float(np.abs(mixed).max())/.999)
            challenge = replay_audio(mixed, classes)
            challenge['insertions'] = references
            track['paired_10dB'] = challenge
            report['tracks'][name] = track
            print(name, track['windows'], 'background windows', flush=True)
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ('downloads', 'model', 'output'):
        parser.add_argument(arg, type=Path)
    args = parser.parse_args()
    args.output.write_text(json.dumps(run(args.downloads, args.model), indent=2)+'\n', encoding='utf-8')
