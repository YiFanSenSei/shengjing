"""Local clip-level baseline using the exact phone TFLite model; no training/upload."""
import argparse
import csv
import hashlib
import io
import json
import math
import os
from pathlib import Path
import time
import zipfile

os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL','2')
import numpy as np
from scipy.io import wavfile
from scipy.signal import resample_poly

TARGETS={'Knock':'door_wood_knock','Alarm clock':'clock_alarm','Doorbell':None}
MODEL_SHA='10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de'
ESC_SHA='661183a6f53ef04f12c9bd618fed0ddc1713280d6c94a5a5431e844ba6f6a21f'
DEMAND={'DLIVING':'46741384d9e434a0bd8b3ec1830b6052','OOFFICE':'7b61cc2d182d5a654cb9c3101ddd4041','DKITCHEN':'7ffbf52d7f4699f96927846103dc8788'}

def digest(path,algorithm='sha256'):
    h=hashlib.new(algorithm)
    with Path(path).open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
    return h.hexdigest()

def read_audio(data):
    rate,x=wavfile.read(io.BytesIO(data))
    if x.dtype.kind=='i':x=x.astype(np.float32)/float(2**(x.dtype.itemsize*8-1))
    elif x.dtype==np.uint8:x=(x.astype(np.float32)-128)/128
    else:x=x.astype(np.float32)
    if x.ndim!=1:raise ValueError('Expected a single channel')
    if not np.all(np.isfinite(x)):raise ValueError('Nonfinite audio')
    factor=math.gcd(rate,16000)
    if rate!=16000:x=resample_poly(x,16000//factor,rate//factor)
    return np.clip(x,-1,1).astype(np.float32)

def mix_at_snr(signal,noise,snr):
    if len(signal)!=len(noise):raise ValueError('Length mismatch')
    signal_rms=float(np.sqrt(np.mean(signal.astype(np.float64)**2)))
    noise_rms=float(np.sqrt(np.mean(noise.astype(np.float64)**2)))
    if min(signal_rms,noise_rms)<=1e-12:raise ValueError('Cannot define SNR for silence')
    scaled=noise*(signal_rms/noise_rms)*10**(-snr/20)
    gain=max(1.,float(np.max(np.abs(signal+scaled)))/.999)
    return ((signal+scaled)/gain).astype(np.float32),gain

def noise_subset(rows):
    chosen=[];seen=set()
    for row in sorted(rows,key=lambda r:r['filename']):
        if int(row['fold'])!=5:continue
        category=row['category']
        if category in TARGETS.values() or category not in seen:chosen.append(row)
        seen.add(category)
    return chosen

def summarize(rows,threshold=.60):
    result={'clips':len(rows),'threshold':threshold,'classes':{}}
    for label,category in TARGETS.items():
        positive=[r for r in rows if category is not None and r['category']==category]
        negative=[r for r in rows if r not in positive]
        hits=sum(r['scores'][label]>=threshold for r in positive)
        fp=[r for r in negative if r['scores'][label]>=threshold]
        categories={}
        for row in fp:categories[row['category']]=categories.get(row['category'],0)+1
        result['classes'][label]={'positives':len(positive),'hits':hits,
            'clip_recall':hits/len(positive) if positive else None,
            'label_negative_clips':len(negative),'label_negative_alerts':len(fp),
            'label_negative_alert_fraction':len(fp)/len(negative) if negative else None,
            'confusing_categories':dict(sorted(categories.items(),key=lambda p:(-p[1],p[0])))}
    return result

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--downloads',type=Path,required=True)
    parser.add_argument('--model',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--noise',action='store_true')
    args=parser.parse_args()
    if digest(args.model)!=MODEL_SHA:raise ValueError('Unexpected model')
    esc=args.downloads/'ESC-50-33c8ce9.zip'
    if digest(esc)!=ESC_SHA:raise ValueError('ESC archive checksum mismatch')
    import tensorflow as tf
    interpreter=tf.lite.Interpreter(model_path=str(args.model),num_threads=2)
    interpreter.allocate_tensors()
    inp=interpreter.get_input_details()[0];out=interpreter.get_output_details()[0]
    window=int(np.prod(inp['shape']));hop=4000
    with zipfile.ZipFile(args.model) as model_zip:
        label_file=next(n for n in model_zip.namelist() if n.endswith('.txt'))
        labels=model_zip.read(label_file).decode().splitlines()
    indices={label:labels.index(label) for label in TARGETS}
    config={'model_sha256':MODEL_SHA,'esc_sha256':ESC_SHA,'window_samples':window,
        'hop_samples':hop,'rate':16000,'threshold':.60,'tensorflow':tf.__version__,
        'numpy':np.__version__,'noise':args.noise,'protocol_version':1}
    args.output.mkdir(parents=True,exist_ok=True)
    config_path=args.output/'config.json'
    if config_path.exists() and json.loads(config_path.read_text())!=config:raise ValueError('Use a new output directory for changed configuration')
    config_path.write_text(json.dumps(config,indent=2),encoding='utf-8')
    cache_path=args.output/'clips.jsonl'
    cached={}
    if cache_path.exists():
        for line in cache_path.read_text().splitlines():
            row=json.loads(line);cached[(row['condition'],row['filename'])]=row
    def infer(audio):
        if len(audio)<window:audio=np.pad(audio,(0,window-len(audio)))
        starts=list(range(0,len(audio)-window+1,hop))
        if starts[-1]!=len(audio)-window:starts.append(len(audio)-window)
        peaks=np.zeros(len(labels),dtype=np.float32)
        for start in starts:
            interpreter.set_tensor(inp['index'],audio[start:start+window].reshape(inp['shape']))
            interpreter.invoke()
            peaks=np.maximum(peaks,interpreter.get_tensor(out['index']).reshape(-1))
        return {label:float(peaks[i]) for label,i in indices.items()}
    with zipfile.ZipFile(esc) as archive,cache_path.open('a',encoding='utf-8') as log:
        prefix=next(n for n in archive.namelist() if n.endswith('/meta/esc50.csv')).removesuffix('meta/esc50.csv')
        rows=list(csv.DictReader(io.StringIO(archive.read(prefix+'meta/esc50.csv').decode())))
        if len(rows)!=2000:raise ValueError('Expected complete ESC-50')
        def run(row,condition,noise=None,snr=None,noise_only=False):
            key=(condition,row['filename'])
            if key in cached:return
            audio=read_audio(archive.read(prefix+'audio/'+row['filename']))
            offset=None;gain=1.
            if noise is not None:
                offset=int(hashlib.sha256(row['filename'].encode()).hexdigest()[:8],16)%(len(noise)-len(audio)+1)
                segment=noise[offset:offset+len(audio)]
                if noise_only:audio=segment.copy()
                else:audio,gain=mix_at_snr(audio,segment,snr)
            start=time.monotonic();scores=infer(audio)
            item={'filename':row['filename'],'category':'background_only' if noise_only else row['category'],'fold':int(row['fold']),
                  'condition':condition,'scores':scores,'noise_offset_samples':offset,'common_gain_divisor':gain,
                  'compute_seconds':time.monotonic()-start}
            cached[key]=item;log.write(json.dumps(item)+'\n');log.flush()
            if len(cached)%50==0:print('Completed clips:',len(cached),flush=True)
        for row in rows:run(row,'clean')
        subset=noise_subset(rows)
        if args.noise:
            for environment,expected in DEMAND.items():
                path=args.downloads/(environment+'_16k.zip')
                if digest(path,'md5')!=expected:raise ValueError('DEMAND checksum mismatch: '+environment)
                with zipfile.ZipFile(path) as noises:
                    name=next(n for n in noises.namelist() if n.lower().endswith('ch01.wav'))
                    noise=read_audio(noises.read(name))
                for row in subset:run(row,environment+'_noise_only',noise,noise_only=True)
                for snr in [20,10,0]:
                    for row in subset:run(row,environment+'_'+str(snr)+'dB',noise,snr)
    records=list(cached.values());conditions=sorted({r['condition'] for r in records})
    summary={'config':config,'conditions':{c:summarize([r for r in records if r['condition']==c]) for c in conditions if not c.endswith('_noise_only')},
             'background_controls':{c:summarize([r for r in records if r['condition']==c]) for c in conditions if c.endswith('_noise_only')},
             'clean_by_fold':{str(f):summarize([r for r in records if r['condition']=='clean' and r['fold']==f]) for f in range(1,6)},
             'noise_subset_files':[r['filename'] for r in subset],
             'matched_clean':summarize([r for r in records if r['condition']=='clean' and r['filename'] in {s['filename'] for s in subset}])}
    (args.output/'summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    print('Complete:',len(records),'clip conditions; summary written',flush=True)

if __name__=='__main__':main()
