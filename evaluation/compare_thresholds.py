"""Exploratory replay of cached model scores, not an independent test."""
import argparse
import json
from pathlib import Path
from audio_baseline import summarize

def compare(rows):
    clean=[r for r in rows if r['condition']=='clean']
    grid={str(t):summarize(clean,t) for t in [.6,.5,.4,.3,.2,.1]}
    conditions={}
    for name in sorted({r['condition'] for r in rows}):
        subset=[r for r in rows if r['condition']==name]
        conditions[name]={str(t):summarize(subset,t)['classes'] for t in [.6,.4]}
    return {'status':'exploratory_same_data_replay','candidate':{'Knock':.4,'Alarm clock':.4,'Doorbell':.6},
            'grid':grid,'conditions':conditions}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('cache',type=Path);p.add_argument('output',type=Path)
    args=p.parse_args();rows=[json.loads(line) for line in args.cache.read_text().splitlines()]
    args.output.write_text(json.dumps(compare(rows),indent=2),encoding='utf-8')
