# 四段公开门铃录音的端侧模型回放

2026-11-23。使用 0.4.0 配套 YAMNet TFLite 模型，门铃阈值固定为 0.60。在四个原始录音中，两个片段至少有一个窗口达到阈值；叠加客厅噪声后，三个信噪比下分别有一个片段达到阈值。声音来源少，三段来自同一上传者，预训练来源重叠情况未知。结果适合定位失败案例。

| 原始录音及来源 | 原始门铃峰值 | 客厅 20 dB | 10 dB | 0 dB | 原始录音中峰值最高的类别 |
|---|---:|---:|---:|---:|---|
| [经典双音，Dub](https://commons.wikimedia.org/wiki/File:Doorbell-classic-dingdong.ogg) | 0.199 | 0.109 | 0.109 | 0.082 | Music |
| [简易双音，Dub](https://commons.wikimedia.org/wiki/File:Doorbell-cheap-dingdong.ogg) | 0.801 | 0.148 | 0.020 | 0.004 | Doorbell |
| [老式响铃，Dub](https://commons.wikimedia.org/wiki/File:Doorbell-old-tring.ogg) | 0.262 | 0.020 | 0.031 | 0.031 | Telephone |
| [电子门铃，drb400atx](https://commons.wikimedia.org/wiki/File:Electronic_doorbell.ogg) | 0.988 | 0.984 | 0.957 | 0.852 | Doorbell |

Dub 的三段原始录音标注 PD-self，电子门铃标注 CC0 1.0。背景使用 [DEMAND 客厅 DLIVING，Thiemann、Ito、Vincent，CC BY-SA 3.0](https://zenodo.org/records/1227121)。仓库保存脚本与数值，原音留在本地。此前 FSD50K 的 144 条候选仅作标签索引，本轮采用上表四个原文件。

## 实验口径

每段录音解码为浮点 PCM，声道取均值并重采样到 16 kHz。窗口 15,600 点，步长 4,000 点，末窗与片尾对齐。峰值取整段所有窗口的最大值。不同类别的峰值可能出现在不同窗口。噪声取客厅第一个声道，每个文件使用可复现的单一偏移和相同背景做三档对照。信噪比按全片 RMS 定义，包含首尾静音；同一录音的多个条件共享声源。四个等长纯背景对照均未触发。

四段原音即使把门铃阈值从 0.60 放宽至 0.40，命中仍为 2/4。下一轮宜补充来自不同设备的门铃，加入电话铃与短音乐作困难负例，再比较识别改动带来的漏报与误报。手机通知、手表送达和真实场景召回需要另行实测。

## 复现

按 [评测环境说明](../evaluation/README.md) 准备同款模型、锁定依赖和 DEMAND 客厅压缩包，在评测环境额外安装 `soundfile==0.13.1`。下载表中各文件的“Original file”，电子门铃用文件名 `Electronic_doorbell.ogg`。脚本逐文件验证来源页 SHA-1，并核对模型与客厅包摘要。

```sh
python evaluation/doorbell_probe.py <yamnet.tflite> <四个OGG所在目录> <DLIVING_16k.zip> <输出JSON路径>
```

[逐文件原始分数、背景偏移及版本](doorbell-public-probe.json)

## 相近声音标签与误报对照（11 月 23 日）

继续使用相同的四段原始门铃。负例取 ESC-50 七类的全部片段，每类 40 条，包含闹钟、时钟滴答、教堂钟、敲门、木门吱呀、警笛和汽车喇叭；另加一段真实电话来电铃，共 281 条。电话音频由 Work With Sounds / Technical Museum of Slovenia 提供，许可为 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)，本地解码、混合声道并重采样到 16 kHz。[Iskra Eta 80 电话铃录音](https://commons.wikimedia.org/wiki/File:WWS_AutomatictelephoneIskraEta80ringing.ogg)

每个窗口取所列模型标签的最大分数，片段中只要有窗口越过或达到阈值就计一次。下表只统计这轮原音。负例按数据集标签判断，数量按片段计。

| 门铃判定标签 | 阈值 | 门铃命中／4 | 干扰片段触发／281 |
|---|---:|---:|---:|
| Doorbell | 0.60 | 2 | 0 |
| Doorbell 或 Ding-dong | 0.60 | 2 | 0 |
| 再加入 Chime | 0.60 | 2 | 0 |
| Doorbell | 0.25 | 3 | 2 |
| Doorbell 或 Ding-dong | 0.25 | 4 | 2 |
| 再加入 Chime | 0.25 | 4 | 2 |

0.25 是看过漏检分数后的探索值。经典双音门铃的 Ding-dong 峰值为 0.262，计入该标签后刚好找回。旧式响铃的 Doorbell 峰值也是 0.262。低阈值下，一段闹钟和一段教堂钟同样触发。真实电话铃的 Telephone 峰值为 0.984，而老式门铃的 Telephone 峰值为 0.996；用电话高分直接排除门铃，会把这个真门铃一并排除。

手机仍沿用 0.60。下一轮用不同设备录制的门铃、闹钟与电话铃验证候选规则，复测客厅背景中的表现，再决定是否提供实验选项。本次 4/4 的分母是用于探索参数的四段已知样本；误触发数不能换算成每小时打扰次数。

使用上一节环境，把电话音频以 `WWS_AutomatictelephoneIskraEta80ringing.ogg` 放到下载目录，与 ESC-50 压缩包和 `commons-doorbells/` 并列。在仓库根目录执行：

```sh
python evaluation/doorbell_labels.py <yamnet.tflite> <下载目录> <输出JSON路径>
```

[候选规则、逐条命中和误触发清单](doorbell-labels.json)
