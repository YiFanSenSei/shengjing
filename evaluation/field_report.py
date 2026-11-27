"""Summarize one observed, continuous field session without raw audio or identifiers."""
import argparse
import json
import math
from pathlib import Path

from evaluation.metrics import evaluate, match_events

KINDS = {'knock', 'doorbell', 'alarm'}


def summarize(session):
    source = session['source']
    if source not in ('field', 'synthetic'):
        raise ValueError('Set source to field or synthetic')
    duration = session['duration_s']
    truth = session['truth']
    phone = session['phone']
    if any(row['label'] not in KINDS for row in truth + phone):
        raise ValueError('Unexpected sound label')
    requests = observed = seen = 0
    for event in phone:
        delivery, wrist = event['delivery'], event['wrist']
        if delivery not in ('requested','blocked','failed') or wrist not in ('seen','missed','unknown'):
            raise ValueError('Unexpected delivery or wrist status')
        if delivery != 'requested' and wrist != 'unknown':
            raise ValueError('Only requested notifications can be observed for delivery')
        if wrist == 'seen':
            if event.get('wrist_s') is not None and not event['at_s'] <= event['wrist_s'] <= duration:
                raise ValueError('Wrist time outside observation window')
        elif event.get('wrist_s') is not None:
            raise ValueError('Unexpected wrist timestamp')
        if event['simulated'] is not False:
            raise ValueError('Exclude simulated reminders from field event data')
        requests += delivery == 'requested'
        observed += wrist != 'unknown'
        seen += wrist == 'seen'
    phone_metrics = evaluate(truth,phone,duration)
    pairs,_ = match_events(truth,phone,duration)
    verified = [(i,j) for i,j in pairs if phone[j]['wrist'] == 'seen']
    latencies = sorted(phone[j]['wrist_s'] - truth[i]['onset_s']
                       for i,j in verified if phone[j].get('wrist_s') is not None)
    return {'source':source, 'duration_s':duration, 'phone':phone_metrics,
            'requested':requests,'blocked':sum(e['delivery']=='blocked' for e in phone),
            'failed':sum(e['delivery']=='failed' for e in phone),
            'wrist_seen':seen,'wrist_missed':observed-seen,'wrist_unknown':requests-observed,
            'wrist_seen_per_observed_request':seen/observed if observed else None,
            'verified_end_to_end_hits':len(verified), 'timed_end_to_end_hits':len(latencies),
            'verified_hits_per_truth':len(verified)/len(truth) if truth else None,
            'end_to_end_latency_p50_s':latencies[math.ceil(.5*len(latencies))-1] if latencies else None,
            'end_to_end_latency_p95_s':latencies[math.ceil(.95*len(latencies))-1] if latencies else None}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('session',type=Path)
    args=parser.parse_args()
    try:
        print(json.dumps(summarize(json.loads(args.session.read_text(encoding='utf-8-sig'))),indent=2,allow_nan=False))
    except (OSError,ValueError,TypeError,KeyError):
        parser.exit(2, 'Invalid session. Check labels, timing, observation status and simulated flag.\n')
