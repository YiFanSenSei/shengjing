"""Download pinned development assets outside version control; stdlib only."""
import argparse
import hashlib
from pathlib import Path
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[1]
GRADLE_SHA='f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6'
MODEL_SHA='10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de'

def download(url,path,digest):
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest()==digest:
        return
    partial=path.with_suffix(path.suffix+'.download')
    with urllib.request.urlopen(url,timeout=120) as response, partial.open('wb') as output:
        while block:=response.read(1024*1024): output.write(block)
    if hashlib.sha256(partial.read_bytes()).hexdigest()!=digest:
        raise RuntimeError('Checksum mismatch; refusing to use downloaded asset')
    partial.replace(path)

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--model',action='store_true');parser.add_argument('--gradle',action='store_true')
    args=parser.parse_args()
    if args.model:
        download('https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/audio_classification/android/lite-model_yamnet_classification_tflite_1.tflite',ROOT/'apps/phone/src/main/assets/yamnet.tflite',MODEL_SHA)
        print('YAMNet ready (SHA-256 verified).')
    if args.gradle:
        archive=ROOT/'tmp/tools/gradle-8.11.1-bin.zip'
        download('https://services.gradle.org/distributions/gradle-8.11.1-bin.zip',archive,GRADLE_SHA)
        target=archive.parent.resolve()
        with zipfile.ZipFile(archive) as bundle:
            for member in bundle.infolist():
                if not (target/member.filename).resolve().is_relative_to(target):
                    raise RuntimeError('Unsafe archive path')
            bundle.extractall(target)
        binary=target/'gradle-8.11.1/bin/gradle'
        binary.chmod(0o755)
        print('Gradle 8.11.1 ready (SHA-256 verified).')
