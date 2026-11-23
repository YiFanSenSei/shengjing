# 协作流程

## 权限与审核

负责人：@YiFanSenSei。普通 PR 模式，无强制分支保护、无强制成功检查。个人私有仓库协作者有写权限；成员只在功能分支开发、不直接推 main、不自行合并，是团队约定，不是平台权限保证。CODEOWNERS 表达审核归属；套餐不支持时不能保证自动请求审核，请在 PR 中手动指定或提醒负责人。

负责人只在检查通过、内容检查完毕后使用 Squash and merge。禁止共享账号、令牌或 GitHub 登录会话。每人使用独立账号，建议开启双重验证；离队时撤销访问。邀请成员前确认准确 GitHub 用户名。

Git 的署名邮箱会出现在公开提交记录中。成员可自行选择公开地址，或在 GitHub Settings → Emails 开启 Keep my email addresses private，并在本地 Git 配置对应的 noreply 邮箱。PR 隐私检查扫描贡献分支的真实历史。

## 每次开发

```sh
git switch main
git pull --ff-only
git switch -c feat/audio-baseline
# 编辑、运行相关测试
git add <明确的文件路径>
python scripts/privacy_guard.py --staged
git commit -m "feat: add audio baseline"
git push -u origin feat/audio-baseline
```

创建以 main 为目标的 PR，按模板填写。每周集成至少一次；功能未完成可使用 Draft PR。不要跨模块批量重构；接口变化先同步其他负责人。不要把 git add . 当成检查内容的替代。

## 本地防护

初次克隆执行 `git config core.hooksPath .githooks`。pre-commit 检查暂存内容，pre-push 检查待推送历史并阻止 main/master 的直接推送。钩子需要 Python 3.11+；可以被绕过或修改，因此不能替代审核。初始化仓库的首次提交由负责人完成。

`.gitignore` 只影响默认暂存行为，不会移除已经跟踪的文件，也无法阻止强制添加。录音、图像、压缩包、表格、模型权重等暂不进 Git；未来确有必要加入已审查资源时，单独提出规则变更 PR。

## 检查能力

隐私检查覆盖常见令牌、私钥、手机号、身份证号形态、邮箱、本机用户路径，以及不允许进入仓库的数据文件类型。既检查当前文件，也检查可达提交历史中的文件快照和提交信息；结果只输出问题类别，不打印匹配内容。它不是通用隐私识别器，不能可靠发现自然语言中的姓名、地址、病情、所有新型密钥或被编码的数据。

邮箱规则跳过 Git 作者和提交者的邮箱字段，以及提交末尾标准 `Co-authored-by: 姓名 <邮箱>` 署名中的邮箱。文件、路径、提交标题、正文及其他尾注继续检查邮箱。令牌、私钥等其他检查也覆盖署名字段。

CI 使用只读权限，不注入项目密钥；不采用 pull_request_target，不运行带秘密的 PR 代码。新接入第三方 Action、依赖和云服务需要负责人检查。基础 CI 文件仍可能被有写权限的人修改；普通 PR 模式下没有不可绕过的安全保证。
