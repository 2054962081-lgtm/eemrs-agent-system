# 批量预问诊评估说明

## 当前评估集规模

当前 `evaluation/data/pre_consultation_eval_cases.jsonl` 共 40 条 case：

| 类型 | 数量 |
| --- | ---: |
| quick_common | 10 |
| quick_red_flag | 6 |
| deep_structured | 8 |
| anti_misleading | 6 |
| special_population | 5 |
| record_generation | 5 |

按模式统计：

| 模式 | 数量 |
| --- | ---: |
| quick | 24 |
| deep | 16 |

其中深度问诊相关 case 目前大多只有用户输入轮次，没有预置 assistant 中间回复。批量评估时建议使用脚本的 `--multi-turn` 模式，让模型真实生成每轮回复并自动补入 history。

## 多轮深度问诊评估

只跑深度结构化问诊：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --multi-turn --case-type deep_structured
```

跑全部 case，并启用多轮模拟：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --multi-turn
```

脚本行为：

- quick case：按 case 中的用户输入逐轮调用。
- deep case：按用户输入逐轮调用，并在末尾追加一次“生成深度问诊总结和科室建议”的请求。
- record_generation case：调用病历草稿生成接口。
- 每轮 assistant 回复会自动补充到下一轮 history。

## 耗时估算

只估算，不实际调用接口：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --estimate-only --multi-turn
```

默认估算参数：

| 请求类型 | 默认耗时 |
| --- | ---: |
| quick 单次请求 | 8 秒 |
| deep 信息收集轮 | 12 秒 |
| deep 总结轮 | 18 秒 |
| 病历草稿生成 | 25 秒 |

当前全量 40 条 case 在 `--multi-turn` 模式下：

```text
预计请求数：86
预计耗时：约 16.2 分钟
```

只跑 `deep_structured` 8 条：

```text
预计请求数：24
预计耗时：约 5.6 分钟
```

可以按实际接口速度调整估算参数：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --estimate-only --multi-turn --quick-seconds 6 --deep-turn-seconds 10 --deep-summary-seconds 15 --record-seconds 20
```

## 建议执行顺序

1. 先跑小样本确认服务、key、数据库和报告输出都正常：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --multi-turn --limit 3
```

2. 再跑快速问诊普通 case：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --multi-turn --case-type quick_common
```

3. 再跑红旗风险 case：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --multi-turn --case-type quick_red_flag
```

4. 再跑深度问诊：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --multi-turn --case-type deep_structured
```

5. 最后跑病历草稿生成：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --case-type record_generation
```

## Python 本机警告

如果本机 Python 输出 `reactpy_jupyter.pth` 权限警告，可使用：

```bash
python -S evaluation/scripts/run_pre_consultation_eval.py --estimate-only --multi-turn
```

该警告来自本机 Python 用户站点包加载，不影响评估脚本本身。
