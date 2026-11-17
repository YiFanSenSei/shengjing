# 声境 Shengjing

面向听障人士及老年听力下降人群的环境声音辅助提醒项目。探索手机感知、事件决策与手表提醒的协作闭环。

当前开发分支包含离线识别手机原型、提醒策略、手表安装/震动验证应用和评测工具。手表通知同步与确认回传尚待真机验证。构建和试用见 [首个原型说明](docs/first-prototype.md)。

## 当前协作模式

仓库为私有。采用普通 PR 流程：功能分支 → PR → @YiFanSenSei 审核 → 仓库负责人合并。

**当前未开启强制分支保护。** GitHub 对本账户私有仓库返回套餐限制；经负责人选择，先用普通 PR。CODEOWNERS、检查和团队约定不会撤销协作者的直接推送或合并权限。未经负责人同意不得直接推送 main、强推、删除分支或自行合并。

## 分工与目录

| 负责人 | 模块 | 目录 |
|---|---|---|
| A | 手机、手表、通信与应用集成 | apps/ |
| B | 音频预处理、声音识别与模型接口 | audio/ |
| C | 事件决策、偏好和提醒策略 | agent/ |
| D | 数据处理、自动评测与系统测试 | evaluation/ |

原始数据、录音、问卷、联系方式和个人设备日志在仓库外保存；这里只保存代码、脱敏说明、合成测试数据的生成脚本。不要上传父目录中的报名资料或整个项目文件夹。

## 开始协作

1. 阅读 [协作流程](CONTRIBUTING.md) 和 [隐私规则](SECURITY.md)。
2. 安装 Git 和 Python 3.11+，克隆本仓库。
3. 执行 `git config core.hooksPath .githooks` 开启本地检查；确保 `python --version` 正常。
4. 执行 `python scripts/privacy_guard.py --history` 和 `python -m unittest discover -s tests`。
5. 从最新 main 创建 `feat/模块-功能`、`fix/问题` 或 `docs/主题` 分支，提交 PR。

Git 提交作者建议使用 GitHub 公开用户名和 GitHub Settings → Emails 中的 noreply 邮箱。不要提交真实姓名、学校邮箱等不希望共享的身份信息。

自动检查是基础防线，无法识别所有个人信息。PR 检查发生在上传后；敏感内容一旦推送，就应按 SECURITY.md 处理，不能认为不合并就没有泄露。
