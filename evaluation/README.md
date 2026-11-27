# 离线公开音频基线

新增 [0.9.0 连续背景与门铃插入结果](../docs/continuous-replay.md) 和 [D 端现场验收单与填表示例](field-protocol.md)。

阶段 04 的电脑端验收工具及结果见[场景策略回放与门铃数据盘点](stage04-replay.md)。

门铃音频实测见[四段公开录音及客厅噪声对照](../docs/doorbell-public-probe.md)。

本目录由数据与评测角色（D）维护，负责数据处理、划分、批量评测和指标计算。真实数据放仓库外，工具与经过检查的汇总结果进入仓库；事件级评测仍使用 `metrics.py`。

使用与手机相同 SHA-256 的 YAMNet TFLite 模型，不训练、不微调、不改变提醒阈值。仅处理本地公开音频，不连接手机、不上传音频。模型路径可用 `python scripts/bootstrap.py --model` 准备。

## 复现

建议使用 Python 3.12 创建独立虚拟环境，并安装 `evaluation/requirements-lock.txt`。`requirements.txt` 列出三项直接依赖，lock 文件保留本次实际环境。

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r evaluation/requirements-lock.txt
./evaluation/download_public_audio.ps1 -Destination ./datasets/downloads
.venv/Scripts/python.exe evaluation/audio_baseline.py --downloads ./datasets/downloads --model ./apps/phone/src/main/assets/yamnet.tflite --output ./datasets/baseline --noise
.venv/Scripts/python.exe -m unittest discover -s evaluation -p test_audio_baseline.py
```

音频压缩包总计约 0.93 GB，推理直接从压缩包读取，无须全部解压。输出包括配置、逐片段 JSONL 和汇总 JSON；不提交逐片段缓存或音频。程序校验模型、ESC-50 和 DEMAND 包，配置一致时可从 JSONL 缓存继续；更换协议时须使用新输出目录。中断若发生在最后一行写入期间，先保留原文件，再检查/移除不完整尾行，不能将损坏缓存当作成功结果。

## 固定协议

- ESC-50 使用官方提交 `33c8ce9eb2cf0b1c2f8bcf322eb349b6be34dbb6` 的全部 2,000 条音频，保留官方 5 折，并逐折报告。不用这些结果调整阈值；任何后续调参需另行指定开发与锁定测试分组。
- 16-bit PCM 按 32768 归一化，单声道重采样至 16 kHz；输入峰值限制到 [-1,1]。模型窗口 15,600 点（0.975 秒），步长 4,000 点（0.25 秒）；尾部增加一个与末尾对齐的完整窗口。取每个目标类别在片段内的最高分，与 0.60 比较。
- 不把数据集片段长度当作事件持续时间。本轮为片段检出率及按标签计算的负片段触发比例，不报告事件级召回、每小时误报或手表端到端延迟。与手机的实际采样时序、麦克风及通知策略不同，结果不等同手机成绩。
- 加噪对照固定取第 5 折全部敲门/闹钟（16 条）及其他每类按文件名排序的第一条（48 条），共 64 条。与相同 64 条的干净结果配对比较；子集不是自然发生率样本。
- DEMAND 客厅、办公室、厨房各取 `ch01.wav`，按文件名 SHA-256 确定背景偏移。全片段 RMS 定义 SNR，分别混合 20/10/0 dB。为防削波，对整段混音共同缩放并记录缩放因子，保持目标/噪声比例。3 环境 × 3 SNR × 64 条 = 576 个混音条件，来自 64 个声源片段，不是 576 个独立样本。
- 未训练模型，背景片段也未用于训练；不同噪声版本始终保留原片段 fold。不能将同一声源的多个混音版本拆成独立训练/测试样本。
- 另跑 3 × 64 个纯背景窗口，使用相同偏移但保持背景原幅度，检查背景自身触发；与混音后的幅度不完全相同。完整运行共 2,768 个片段条件，其中干净 2,000、混音 576、背景对照 192。

## 边界与来源

ESC-50 是单标签数据，未标注其他目标声不代表目标声绝对不存在；负片段触发应人工听检后再称为确定误报。ESC-50 没有门铃正类，门铃召回保持 null，不写成 0% 或 100%。本轮没有接入 FSD50K 或训练新模型。

[ESC-50 官方仓库](https://github.com/karolpiczak/ESC-50)：完整集合 CC BY-NC 3.0，保留压缩包中的 LICENSE 和逐条署名。本轮为内部研究评测，音频不随代码发布。引用 Piczak, ESC: Dataset for Environmental Sound Classification, ACM Multimedia 2015。

[DEMAND 官方发布](https://zenodo.org/records/1227121)：CC BY-SA 3.0，作者 Thiemann、Ito、Vincent。下载脚本对照其官方 MD5；合成声音只在内存中生成，本轮不对外分发混音。

[YAMNet 官方教程](https://www.tensorflow.org/hub/tutorials/yamnet)：基于 AudioSet 的预训练模型。预训练数据与公开测试音频的潜在来源重叠未完成核查，不能宣称严格无泄漏的现场泛化成绩。关键产品结论仍需独立自采和连续场景验证。
