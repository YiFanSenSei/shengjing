"""Event-level evaluation. Input is local JSON, raw audio is never needed here."""
import argparse
import json
import math
from pathlib import Path

def evaluate(truth,predictions,duration_s,tolerance_s=2.0):
    if not math.isfinite(duration_s) or duration_s<=0 or tolerance_s<0 or not math.isfinite(tolerance_s):
        raise ValueError('Invalid duration or tolerance')
    for event in truth:
        if not isinstance(event['label'],str) or not (0<=event['onset_s']<=event['offset_s']<=duration_s):
            raise ValueError('Invalid reference interval')
    for event in predictions:
        if not isinstance(event['label'],str) or not (0<=event['at_s']<=duration_s):
            raise ValueError('Invalid prediction time')
    # Chronological predictions; nearest unmatched same-label interval wins.
    matched=set();latencies=[];false_alerts=0;duplicates=0
    for prediction in sorted(predictions,key=lambda x:x['at_s']):
        candidates=[i for i,event in enumerate(truth)
            if event['label']==prediction['label'] and event['onset_s']<=prediction['at_s']<=event['offset_s']+tolerance_s]
        available=[i for i in candidates if i not in matched]
        if available:
            i=min(available,key=lambda i:abs(prediction['at_s']-truth[i]['onset_s']))
            matched.add(i);latencies.append(prediction['at_s']-truth[i]['onset_s'])
        else:
            false_alerts+=1
            duplicates+=bool(candidates)
    by_class={}
    for label in sorted({x['label'] for x in truth}|{x['label'] for x in predictions}):
        count=sum(x['label']==label for x in truth)
        hit=sum(truth[i]['label']==label for i in matched)
        by_class[label]={'events':count,'detected':hit,'recall':hit/count if count else None}
    def percentile(q):
        if not latencies:return None
        ordered=sorted(latencies);return ordered[max(0,math.ceil(q*len(ordered))-1)]
    return {'events':len(truth),'detected':len(matched),'recall':len(matched)/len(truth) if truth else None,
        'false_alerts':false_alerts,'false_alerts_per_hour':false_alerts/(duration_s/3600),
        'duplicate_alerts':duplicates,'latency_p50_s':percentile(.5),'latency_p95_s':percentile(.95),'by_class':by_class}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description='Evaluate local event annotations; do not commit personal data.')
    parser.add_argument('input',type=Path)
    args=parser.parse_args()
    try:
        data=json.loads(args.input.read_text(encoding='utf-8'))
        print(json.dumps(evaluate(data['truth'],data['predictions'],data['duration_s']),ensure_ascii=False,indent=2))
    except (ValueError,KeyError,TypeError,OSError):
        raise SystemExit('Invalid input. Expected duration_s, truth intervals, and prediction timestamps.')
