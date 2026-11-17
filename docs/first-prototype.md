# 首个真机验证版本

## 范围

- 手机应用：本机 YAMNet 模型识别 Knock、Doorbell、Alarm clock；前台麦克风服务、通知、模拟事件、确认和误报反馈。
- 策略：初始分数阈值0.60（未经实地校准，不等于60%准确率）；连续事件合并、同类15秒冷却、类别开关、暂停真实提醒。暂停模式会暂停所有真实提醒，包括闹钟，模拟测试仍可发送。
- 手表验证应用：仅验证安装、启动、振动器；无手机通信。手机通知能否由配套健康应用同步到 OPPO Watch X2，以及确认按钮能否回传，需真机验证。
- 评测：本地 JSON 事件标注计算逐类召回、误报/小时、重复提醒、延迟分位数。此版本未集成自动记录手机/手表全链路时间，不声称已有端到端成绩。

目前状态是开发原型，不支持姓名呼叫，不提供医疗诊断或安全报警保证。确认和误报只保存在本次进程内存，退出进程后清空；误报不会自动修改模型或阈值。UI和策略偏好也暂不跨进程保存。

## 构建

需要 JDK 17、Python 3.11+ 和 Android SDK（platforms;android-35、build-tools;35.0.0）。可由 Android Studio 安装 SDK 并设置 ANDROID_HOME；SDK许可由开发者阅读确认。

```sh
python scripts/bootstrap.py --gradle --model
# Linux/macOS
tmp/tools/gradle-8.11.1/bin/gradle :engine:policyTest :phone:assembleDebug :watchProbe:assembleDebug
# Windows PowerShell
./tmp/tools/gradle-8.11.1/bin/gradle.bat :engine:policyTest :phone:assembleDebug :watchProbe:assembleDebug
```

Android Studio 打开仓库根目录，选择本地下载的 Gradle 8.11.1 作为 Gradle distribution。首次依赖下载需要网络。此原型暂未提交 Gradle Wrapper JAR，统一使用校验下载脚本。

CI 编译产物位于本仓库 Actions → Android prototype → Artifacts，有效期7天，只有获授权访问私有仓库的人可下载；不发布到公开 Release。

## 手机验证步骤

1. 安装 phone-debug.apk，允许系统安装提示；打开“声境·测试版”。
2. 先点击模拟提醒，按提示授权通知后再次点击；确认手机显示提醒。
3. 在手表配套健康应用的通知设置中允许声境通知，保持手表正常配对；分别观察显示、震动、操作按钮。通知同步不意味着支持自定义震动或双向通信。
4. 点击提醒中的“已看到”，观察手机应用反馈文字；若手表没有操作按钮，仅记录为能力不支持/待确认，不能记为回传成功。
5. 点击开始监听，授权麦克风后再点一次；实际敲门、门铃与闹钟分别测试。模拟按钮不算真实识别证据。
6. 点击停止，确认持续监听通知消失、麦克风使用指示结束。测试锁屏2分钟、10分钟、系统关闭麦克风权限、退出重进、断开手表。
7. 不在公共场所长时间录制测试；本应用没有网络权限，不保存原始录音。

通知震动受系统通知渠道、勿扰模式、手表通知同步策略影响；本版本不绕过这些设置。通知渠道一旦创建，修改代码里的震动模式不一定覆盖系统已有设置。

### 短时锁屏通知测试

0.1.1 增加“10 秒后发送模拟敲门（锁屏测试）”：允许通知后点击，看到“已安排”再手动锁屏，观察手表显示、振动与确认回传。可返回应用点击“取消延迟测试”；重复安排会替换之前的任务，只保留一次待发送提醒。取消后等待超过 10 秒，确认没有新模拟通知。

测试只在内存排队，进程被终止后不会恢复。为避免锁屏后处理器休眠推迟计时，使用最长 20 秒的局部唤醒锁，发送、取消或失败时提前释放，不保持屏幕点亮。该测试只验证短时锁屏通知传递，不代表进入深度休眠、长期后台监听或真实声音识别通过。

### 已有设备试测记录

2026-11-16，Find X8 Pro（Android 16）与 Watch X2：用户确认三类模拟通知可显示、振动，手表确认及误报按钮可回传；三类振动节奏有细微差别，尚未验证不看屏幕辨认的能力。需同时开启手机通知振动和 OPPO 健康内声境的单独通知同步开关。0.1.1 的延迟 10 秒短时锁屏模拟通知测试由用户确认成功。敲桌测试未触发真实识别，该项暂缓完善；真实声音识别和长期后台运行仍未通过验证。

## 0.2.0 阶段：识别诊断与监听稳定性

打开“显示识别诊断”可查看音频窗口准备、有效识别次数、输入电平、推理耗时、模型最高分类、三类目标当前分数和本次监听峰值，以及策略未提醒原因。分数不是准确率，dBFS 是数字音频电平而非环境声压分贝。未返回的类别显示“未返回”，不会伪装成零分。诊断只留在进程内存，不生成音频文件或日志，不上传数据。

模型输出包含全部类别与低分结果，以便诊断；提醒仍使用原有 0.60 阈值。首次采满一个窗口才开始推理；没有新音频时不重复推理旧窗口，连续 3 秒无新音频时显示提示。异常按权限、模型加载、音频启动、采集、推理阶段显示错误类别，不显示原始异常内容。旧监听会话的回调与结束清理不能覆盖新会话状态。

后续遵循阶段验收：先开发、自动测试、静态检查、生成固定签名 APK，再集中安排一次真机验收。不会因单个按钮改动就要求用户连接设备。此阶段真机验收清单见 [阶段验收计划](stage-validation.md)。

## 手表自定义应用能力验证

先确认设置里实际显示的系统版本。国内与国际 Watch X2 系统不同，不预设可用 Google Play services。只有设备提供正常开发者调试方式时，再使用 Android 官方 adb 配对/安装 watchProbe-debug.apk。不要解锁、刷机或为此安装来路不明的助手。

逐项记录：能否进入开发者选项；是否提供 USB/无线/蓝牙调试；电脑是否能连接；APK能否安装；应用能否打开；按按钮能否实际震动。调试完成后关闭不再使用的调试权限。设备序列号、IP、配对码只用于本机操作，不提交到仓库。

## 评测输入与口径

本地 JSON 包含 duration_s、truth（label/onset_s/offset_s）和 predictions（label/at_s）。同类预测只匹配一个真值事件；允许事件结束后2秒的匹配容差，不允许预测早于事件开始。按预测时间顺序匹配最近的未匹配事件。重复提醒单列，并计入误报数。P50/P95使用nearest-rank，只针对成功匹配事件；漏报体现在召回中。以实际手表提醒时刻作为 at_s 才能称为端到端延迟。跨设备计时必须先对齐时钟。

## 依赖与来源

- AGP 8.9.2、Gradle 8.11.1、JDK17、SDK35：[Android官方兼容说明](https://developer.android.com/build/releases/agp-8-9-0-release-notes)。
- YAMNet/classification：Google/TensorFlow 预训练模型；模型按校验值下载，不提交 Git；该模型不是团队训练成果。[官方教程](https://developers.google.com/codelabs/tflite-audio-classification-basic-android)。
- Task Audio 0.4.4：[官方Java接入说明](https://developers.google.com/edge/litert/libraries/task_library/audio_classifier)。
- [Android麦克风前台服务要求](https://developer.android.com/about/versions/14/changes/fgs-types-required)。
- [OPPO国内Watch X2页面](https://store.oppo.com/cn/app/product/index?skuId=32731)、[国际版规格](https://www.oppo.com/en/accessories/watch-x2/specs/)。

模型与库的许可需在对外发行前整理成完整第三方声明；当前仅用于私有开发验证。模型固定SHA-256见 scripts/bootstrap.py。
