# 事件与提醒策略（C）

负责优先级、重复合并、冷却、偏好和反馈逻辑。用模拟事件独立验证；第一阶段不依赖付费 API。

- 规则、原因名称和现场口径：[提醒规则约定](../docs/policy-contract.md)
- 场景与偏好回放：[回放表与复现](../docs/policy-replay.md)
- 回放命令：`gradle :engine:policyReplay`；单元检查：`gradle :engine:policyTest`
