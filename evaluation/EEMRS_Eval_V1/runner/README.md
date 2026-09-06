# EEMRS Eval Runner V1

本目录保存 `EEMRS-Eval-v1.0` 的第一版 Eval Runner（评测执行器）。

默认只加载 DEV：

```powershell
python -S evaluation\EEMRS_Eval_V1\runner\tools\run_eval.py --split dev --agent-mode http --phase smoke
```

Frozen Holdout（冻结测试集）只能通过显式参数 `--split holdout` 加载。本轮禁止运行 Holdout，不得用 Holdout 做 Prompt、RAG、Workflow 或 Bad Case 调优。

`oracle` 和 `broken` 模式只用于验证 Evaluator，不代表真实 Agent Baseline。

