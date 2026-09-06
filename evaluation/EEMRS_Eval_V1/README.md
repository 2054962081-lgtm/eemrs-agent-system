# EEMRS-Eval-v1.0

本目录保存 EEMRS Agent System 的离线评测数据集。数据集用于评估预问诊、Safety Gate（安全门控）、报告趋势分析和医生病历草稿生成的一致性。

数量：Consultation 120，Safety 80，Report 50，Doctor Draft 50；Dev 190，Holdout 110。

运行校验：

```powershell
python -S evaluation\EEMRS_Eval_V1\tools\validate_dataset.py
python -S evaluation\EEMRS_Eval_V1\tools\summarize_dataset.py
```

注意：Semantic Validation（语义校验）PASS 不等于 Medical Ground Truth Correct（医学标准答案正确）。仍标记为 `NEEDS_MEDICAL_REVIEW` 的项目需要医学知识源或专家复核。

## 局部优化说明

本轮新增 Safety Challenge（安全挑战）、Conditional Slot（条件信息槽位）、Resolution Policy（信息解决策略）、Hidden Patient Profile（隐藏患者画像）和 Source Mapping（来源映射）校验。Report slice `CLEAR_IMPROVE_WORSEN` 已重命名为 `CLEAR_DIRECTION_CHANGE`。

## Final Dataset Hardening

当前版本已冻结为 `EEMRS-Eval-v1.0`。DEV / HOLDOUT 已通过 deterministic stratified split（确定性分层切分）重建，Holdout 标记为 `FROZEN_HOLDOUT`，不得用于调参、Prompt 修改、RAG 调优、Workflow 调优或 Bad Case 反馈训练。日常工具默认应加载 DEV；Holdout 只能通过显式参数加载，且 README / Report 不应打印完整 Holdout Ground Truth。
