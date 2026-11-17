"""Render aggregate public-data results without copying per-clip records."""
import argparse
import json
from pathlib import Path

def rate(item):
    return '无正样本，不能计算' if item['clip_recall'] is None else f"{item['hits']}/{item['positives']}（{item['clip_recall']:.1%}）"

def render(summary):
    clean=summary['conditions']['clean']['classes']
    lines=['# 首轮公开音频基线：ESC-50 + DEMAND','',
           '评测日期：2026-11-17。使用手机同款 YAMNet TFLite；阈值固定 0.60。本轮未训练、微调或改动手机模型。','',
           '## 干净音频：全部 2,000 条','',
           '| 目标 | 正片段检出 | 标签负片段触发 |','|---|---|---|']
    for label,item in clean.items():
        lines.append(f"| {label} | {rate(item)} | {item['label_negative_alerts']}/{item['label_negative_clips']} |")
    lines+=['','按标签计算的负片段触发是疑似误报，单标签数据可能漏标重叠声音，仍需听检。不是每小时误报，也不是事件级或手机现场准确率。','',
            '## 官方折的分项结果','', '| Fold | 敲门检出 | 闹钟检出 |','|---|---|---|']
    for fold,group in summary['clean_by_fold'].items():
        lines.append(f"| {fold} | {rate(group['classes']['Knock'])} | {rate(group['classes']['Alarm clock'])} |")
    lines+=['','## 配对加噪对照','',
            '同一组 64 条第 5 折音频：16 条目标正片段、48 条其他类别片段。各环境复用相同原片段，不把 576 个混音条件算成 576 个独立样本。SNR 使用全片段 RMS，未采用事件活动区间。','',
            '| 条件 | 敲门检出 | 闹钟检出 | 敲门负片段触发 | 闹钟负片段触发 |','|---|---|---|---|---|']
    groups={'matched_clean':summary['matched_clean'],**{k:v for k,v in summary['conditions'].items() if k!='clean'}}
    for condition,group in groups.items():
        knock=group['classes']['Knock'];alarm=group['classes']['Alarm clock']
        lines.append(f"| {condition} | {rate(knock)} | {rate(alarm)} | {knock['label_negative_alerts']}/{knock['label_negative_clips']} | {alarm['label_negative_alerts']}/{alarm['label_negative_clips']} |")
    lines+=['','## 纯背景声对照','',
            '使用同一组背景偏移，单独输入原始背景片段（未按混音比例调整幅度）；各环境 64 个窗口，可能重叠，不能视作独立场景。','',
            '| 背景 | 敲门触发 | 闹钟触发 | 门铃触发 |','|---|---|---|---|']
    for name,group in summary.get('background_controls',{}).items():
        counts=[str(group['classes'][k]['label_negative_alerts'])+'/'+str(group['clips']) for k in ['Knock','Alarm clock','Doorbell']]
        lines.append('| '+name+' | '+' | '.join(counts)+' |')
    lines+=['','加噪后部分闹钟检出提高只说明这一小组混音的模型响应变化，不能推出噪声普遍有利；还可能受到输入幅度和背景频谱影响。','', '## 已识别的混淆候选','']
    for label,item in clean.items():lines.append(f"- {label}："+('；'.join(f'{k} {v} 条' for k,v in item['confusing_categories'].items()) or '本轮未触发标签负片段')+'。')
    lines+=['','## 结论与下一步','',
            '- 公开数据已实际用于基线评测，而非只收集入口；没有用于模型训练。',
            '- 当前默认阈值存在漏检，不能直接称为成熟识别能力；先检查漏检片段分数与混淆样本，再设计阈值校准。',
            '- 门铃没有正样本证据，后续需要许可明确的门铃数据与自采；不据此给门铃准确率。',
            '- 此轮已查看全部五折的结果，后续不能把第 5 折称为从未查看的最终测试集；可以按折做明确的开发实验，产品最终测试另建独立自采保留集。',
            '- 预训练与公开数据潜在来源重叠未核查。合成噪声结果不能替代真实房间、距离、手机麦克风或长期后台测试。','',
            '复现方法、精确版本、许可与指标定义见 [评测说明](../evaluation/README.md)。汇总数值见 [JSON](audio-baseline-summary.json)。','',
            '原始来源：[ESC-50](https://github.com/karolpiczak/ESC-50)、[DEMAND](https://zenodo.org/records/1227121)、[YAMNet](https://www.tensorflow.org/hub/tutorials/yamnet)。音频不进入 Git。','']
    return '\n'.join(lines)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('summary',type=Path);parser.add_argument('output',type=Path)
    args=parser.parse_args();args.output.write_text(render(json.loads(args.summary.read_text())),encoding='utf-8')
