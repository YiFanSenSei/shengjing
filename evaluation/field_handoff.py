"""Join a phone file and independent observations; print only aggregate results."""
import argparse
import json
import math
from pathlib import Path
import re

from evaluation.field_report import KINDS, summarize


def seconds(value):
    return type(value) in (int, float) and math.isfinite(value) and value >= 0


def score(device, observer, *, source='field'):
    if device['schema'] != 'shengjing.phone.v1' or observer['schema'] != 'shengjing.observer.v1':
        raise ValueError('文件版本不匹配')
    if not re.fullmatch('[a-f0-9]{8}', device['round']) or device['round'] != observer['round']:
        raise ValueError('两个文件的场次编号不一致')
    duration = device['duration_s']
    if not seconds(duration) or duration == 0 or observer['complete'] is not True or observer.get('unfinished'):
        raise ValueError('需要完整的观察场次')
    if not seconds(observer['observed_s']) or observer['observed_s'] < duration:
        raise ValueError('观察结束早于手机场次，请将本轮留作定性记录')
    if type(device['simulated_excluded']) is not int or device['simulated_excluded'] < 0:
        raise ValueError('模拟计数无效')
    if not isinstance(device['phone'], list) or len(device['phone']) > 100:
        raise ValueError('手机事件列表无效')
    rows = {}
    last = 0
    for number, record in enumerate(device['phone'], 1):
        at = record['at_s']
        if type(record['n']) is not int or record['n'] != number or not seconds(at) or not last <= at <= duration:
            raise ValueError('手机事件序号或时间无效')
        rows[number] = {'label':record['label'], 'at_s':at, 'delivery':record['delivery'],
                        'simulated':False, 'wrist':'unknown'}
        last = at
    used = set()
    if not isinstance(observer['wrist'],list) or not isinstance(observer['truth'],list):
        raise ValueError('观察记录格式无效')
    for item in observer['wrist']:
        number = item['n']
        if type(number) is not int or number not in rows or number in used:
            raise ValueError('腕上观察引用了不存在或重复的手机序号')
        used.add(number)
        rows[number]['wrist'] = item['wrist']
        at = item.get('wrist_s')
        if at is not None and not seconds(at):
            raise ValueError('腕上时间无效')
        rows[number]['wrist_s'] = at
    for item in observer['truth']:
        if item['label'] not in KINDS or not all(seconds(item[k]) for k in ('onset_s','offset_s')):
            raise ValueError('真实声音记录无效')
    result = summarize({'source':source,'duration_s':duration,'phone':list(rows.values()),'truth':observer['truth']})
    result['simulated_excluded'] = device['simulated_excluded']
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('device',type=Path)
    parser.add_argument('observer',type=Path)
    parser.add_argument('--practice',action='store_true',help='label a rehearsal with generated events as synthetic')
    args = parser.parse_args()
    try:
        docs = [json.loads(path.read_text(encoding='utf-8-sig')) for path in (args.device,args.observer)]
        print(json.dumps(score(*docs,source='synthetic' if args.practice else 'field'), ensure_ascii=False, indent=2, allow_nan=False))
    except (ValueError,KeyError,TypeError,OSError):
        parser.exit(2, '无法计分，请核对双文件格式、场次编号、完整观察标记与事件时间。\n')
