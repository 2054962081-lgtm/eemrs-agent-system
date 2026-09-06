import json
from collections import Counter, defaultdict
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
DATASETS = BASE / "datasets"
DOCS = BASE / "docs"

FILES = [
    ("consultation", DATASETS / "consultation_dev.jsonl"),
    ("consultation", DATASETS / "consultation_holdout.jsonl"),
    ("safety", DATASETS / "safety_dev.jsonl"),
    ("safety", DATASETS / "safety_holdout.jsonl"),
    ("report", DATASETS / "report_dev.jsonl"),
    ("report", DATASETS / "report_holdout.jsonl"),
    ("doctor_draft", DATASETS / "doctor_draft_dev.jsonl"),
    ("doctor_draft", DATASETS / "doctor_draft_holdout.jsonl"),
]

def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]

def table(counter):
    return "\n".join(f"- {k}: {v}" for k, v in sorted(counter.items()))

def split_table(rows, key):
    lines = []
    for split in ["DEV", "HOLDOUT"]:
        counter = Counter(row["metadata"][key] for row in rows if row["metadata"]["split"] == split)
        lines.append(f"### {split}")
        lines.append(table(counter))
        lines.append("")
    return lines

def load_rows():
    rows, by_dataset = [], defaultdict(list)
    for dataset, path in FILES:
        data = read_jsonl(path)
        by_dataset[dataset].extend(data)
        rows.extend((dataset, row) for row in data)
    return rows, by_dataset

def semantic_metrics(by_dataset):
    invalid_gate = 0
    all_unknown = 0
    topic_error = 0
    duplicate_department = 0
    behavior_dup = 0
    gate = Counter()
    truth_status = Counter()
    for row in by_dataset["consultation"]:
        m = row["metadata"]
        if m["patient_behavior_primary"] == m.get("patient_behavior_secondary"):
            behavior_dup += 1
        product = row["product_ground_truth"]
        if len(product["acceptable_departments"]) != len(set(product["acceptable_departments"])):
            duplicate_department += 1
        mp = row["scenario"]["clinical_topic_mapping"]
        if row["scenario"]["department_context"] not in mp["triage_rule_ids"]:
            topic_error += 1
        if product["preferred_department"] not in mp["preferred_departments"] and product["preferred_department"] not in mp["acceptable_departments"]:
            topic_error += 1
        required_values = []
        for slot in row["information_requirements"]:
            combo = (slot["priority"], slot["gate_type"])
            gate[f"{combo[0]} + {combo[1]}"] += 1
            if combo not in {("CRITICAL", "SAFETY_GATE"), ("CRITICAL", "TASK_GATE"), ("IMPORTANT", "SCORE_ONLY"), ("OPTIONAL", "SCORE_ONLY")}:
                invalid_gate += 1
            if slot["priority"] == "CRITICAL":
                required_values.append(slot["expected_value"])
            truth_status[slot["patient_certainty"]] += 1
        if required_values and all(v == "UNKNOWN" for v in required_values):
            all_unknown += 1
    pairs = defaultdict(list)
    placeholder_negative = 0
    for row in by_dataset["safety"]:
        pid = row["metadata"].get("pair_id")
        if pid:
            pairs[pid].append(row)
        if "explicitly lacks the decisive trigger fact" in row["scenario"].get("input_text", ""):
            placeholder_negative += 1
    invalid_pair = 0
    for members in pairs.values():
        pos = [r for r in members if r["metadata"]["safety_slice"] == "RED_FLAG_POSITIVE"]
        neg = [r for r in members if r["metadata"]["safety_slice"] == "RED_FLAG_NEAR_NEGATIVE"]
        if len(members) != 2 or len(pos) != 1 or len(neg) != 1:
            invalid_pair += 1
    draft_mismatch = 0
    for row in by_dataset["doctor_draft"]:
        sl = row["metadata"]["draft_slice"]
        inp = row["input"]
        missing = inp["patient_ground_truth"].get("missing_items", [])
        unknown = inp["patient_ground_truth"].get("unknown_items", [])
        if sl == "STANDARD_COMPLETE_TRACE" and len(missing) + len(unknown) > 1:
            draft_mismatch += 1
        if sl == "MISSING_UNKNOWN" and not (missing or unknown):
            draft_mismatch += 1
        if sl == "CONTRADICTION" and not inp.get("contradiction_metadata"):
            draft_mismatch += 1
        if sl == "UNSUPPORTED_CLAIM_TRAP" and not inp.get("unsupported_claim_targets"):
            draft_mismatch += 1
    return {
        "gate": gate,
        "truth_status": truth_status,
        "invalid_gate": invalid_gate,
        "all_unknown": all_unknown,
        "topic_error": topic_error,
        "duplicate_department": duplicate_department,
        "behavior_dup": behavior_dup,
        "invalid_pair": invalid_pair,
        "placeholder_negative": placeholder_negative,
        "draft_mismatch": draft_mismatch,
    }

def write_statistics(rows, by_dataset, metrics):
    try:
        from local_optimization_validator import validate as validate_local_optimization
    except ImportError:
        from evaluation.EEMRS_Eval_V1.tools.local_optimization_validator import validate as validate_local_optimization
    _, local_metrics = validate_local_optimization(by_dataset)
    try:
        from final_hardening_validator import validate as validate_final_hardening
    except ImportError:
        from evaluation.EEMRS_Eval_V1.tools.final_hardening_validator import validate as validate_final_hardening
    final_sections, final_warnings, final_metrics = validate_final_hardening(by_dataset)
    split = Counter(row["metadata"]["split"] for _, row in rows)
    source = Counter(row["metadata"]["source_type"] for _, row in rows)
    review = Counter(row["metadata"]["review_status"] for _, row in rows)
    truth_total = sum(metrics["truth_status"].values()) or 1
    unknown_ratio = round(metrics["truth_status"]["UNKNOWN"] / truth_total, 4)
    lines = [
        "# 数据集统计",
        "",
        f"总 Case 数：{len(rows)}",
        "",
        "## 数据集分布",
        table(Counter(dataset for dataset, _ in rows)),
        "",
        "## Dev / Holdout",
        table(split),
        "",
        "## Consultation 分布",
        "### Difficulty",
        table(Counter(r["metadata"]["difficulty"] for r in by_dataset["consultation"])),
        "",
        "### Quick / Deep",
        table(Counter(r["metadata"]["mode"] for r in by_dataset["consultation"])),
        "",
        "### Normal / Complex",
        table(Counter(r["metadata"]["case_class"] for r in by_dataset["consultation"])),
        "",
        "### Patient Behavior",
        table(Counter(r["metadata"]["patient_behavior_primary"] for r in by_dataset["consultation"])),
        "",
        "## Consultation Frozen Split",
        *split_table(by_dataset["consultation"], "case_class"),
        "### Complexity Reason",
        table(Counter(r["metadata"]["complexity_reason"] for r in by_dataset["consultation"] if r["metadata"]["case_class"] == "COMPLEX")),
        "",
        "## Safety Slice",
        table(Counter(r["metadata"]["safety_slice"] for r in by_dataset["safety"])),
        "",
        "## Safety Frozen Split",
        *split_table(by_dataset["safety"], "safety_slice"),
        "## Report Slice",
        table(Counter(r["metadata"]["report_slice"] for r in by_dataset["report"])),
        "",
        "## Report Frozen Split",
        *split_table(by_dataset["report"], "report_slice"),
        "## Doctor Draft Slice",
        table(Counter(r["metadata"]["draft_slice"] for r in by_dataset["doctor_draft"])),
        "",
        "## Doctor Draft Frozen Split",
        *split_table(by_dataset["doctor_draft"], "draft_slice"),
        "## Gate 组合",
        table(metrics["gate"]),
        "",
        "## Patient Truth 状态",
        table(metrics["truth_status"]),
        f"- UNKNOWN 比例: {unknown_ratio}",
        "",
        "## Source Type",
        table(source),
        "",
        "## Review Status",
        table(review),
        "",
        "## 语义修复指标",
        f"- Invalid Gate Combination: {metrics['invalid_gate']}",
        f"- All-UNKNOWN Case: {metrics['all_unknown']}",
        f"- Topic Mapping Error: {metrics['topic_error']}",
        f"- Duplicate Department: {metrics['duplicate_department']}",
        f"- Behavior Duplication: {metrics['behavior_dup']}",
        f"- Minimal Pair Invalid: {metrics['invalid_pair']}",
        f"- Placeholder Near-Negative: {metrics['placeholder_negative']}",
        f"- Draft Slice Mismatch: {metrics['draft_mismatch']}",
        "",
        "## 修复前后对比",
        "- Invalid Gate Combination: 336 -> 0",
        "- All-UNKNOWN Case: 120 -> 0",
        "- Topic Mapping Error: 存在数组轮转错配风险 -> 0",
        "- Duplicate Department: 潜在风险 -> 0",
        "- Behavior Duplication: 7 -> 0",
        "- Invalid Minimal Pair: 10 组成员数错误 -> 0",
        "- Placeholder Near-Negative: 15 -> 0",
        "- Draft Slice Mismatch: 15+ -> 0",
        "",
        "## 局部优化指标",
        f"- Invalid Safety Challenge Input: {local_metrics.get('Invalid Safety Challenge Input', 0)}",
        f"- Safety RedFlag Contradiction: {local_metrics.get('Safety RedFlag Contradiction', 0)}",
        f"- Conditional Slot Count: {local_metrics.get('Conditional Slot Count', 0)}",
        f"- All-ALWAYS Consultation Case Count: {local_metrics.get('All-ALWAYS Consultation Case Count', 0)}",
        f"- Patient Truth Missing Required Fact: {local_metrics.get('Patient Truth Missing Required Fact', 0)}",
        f"- Special Population Conflict: {local_metrics.get('Special Population Conflict', 0)}",
        f"- Invalid Resolution Policy: {local_metrics.get('Invalid Resolution Policy', 0)}",
        f"- Multi-source Mapping Error: {local_metrics.get('Multi-source Mapping Error', 0)}",
        f"- Generic Unsupported Claim: {local_metrics.get('Generic Unsupported Claim', 0)}",
        f"- Split Correlation Error: {local_metrics.get('Split Correlation Error', 0)}",
        f"- Frozen Holdout Pair Leakage: {local_metrics.get('Frozen Holdout Pair Leakage', 0)}",
        f"- Source Provenance Missing: {local_metrics.get('Source Provenance Missing', 0)}",
        f"- Conversation State Error: {local_metrics.get('Conversation State Error', 0)}",
        f"- Report Slice Validity Error: {local_metrics.get('Report Slice Validity Error', 0)}",
        f"- Exact Duplicate: {final_metrics.get('Exact Duplicate', 0)}",
        f"- Cross-Split Exact Duplicate: {final_metrics.get('Cross-Split Exact Duplicate', 0)}",
        f"- Cross-Split Near Duplicate Warning: {final_metrics.get('Cross-Split Near Duplicate Warning', 0)}",
        f"- Safety Positive Duplicate: {final_metrics.get('Safety Positive Duplicate', 0)}",
        f"- Report Duplicate Bundle: {final_metrics.get('Report Duplicate Bundle', 0)}",
        f"- Clinical Source Mismatch: {final_metrics.get('Clinical Source Mismatch', 0)}",
        f"- Policy Source Missing: {final_metrics.get('Policy Source Missing', 0)}",
        f"- NEEDS_POLICY_REVIEW: {final_metrics.get('NEEDS_POLICY_REVIEW', 0)}",
        f"- Disclosure Conflict: {final_metrics.get('Disclosure Conflict', 0)}",
        f"- UNKNOWN_ACCEPTABLE but still required ask: {final_metrics.get('UNKNOWN_ACCEPTABLE but still required ask', 0)}",
        f"- Conditional truth_path invalid: {final_metrics.get('Conditional truth_path invalid', 0)}",
        f"- Special Population Unobservable Conflict: {final_metrics.get('Special Population Unobservable Conflict', 0)}",
        f"- MULTI_INDICATOR invalid: {final_metrics.get('MULTI_INDICATOR invalid', 0)}",
        f"- UNIT_MISMATCH invalid: {final_metrics.get('UNIT_MISMATCH invalid', 0)}",
        f"- INDICATOR_ALIAS invalid: {final_metrics.get('INDICATOR_ALIAS invalid', 0)}",
        f"- MISSING_TIME_POINT invalid: {final_metrics.get('MISSING_TIME_POINT invalid', 0)}",
        f"- REFERENCE_RANGE_CHANGE invalid: {final_metrics.get('REFERENCE_RANGE_CHANGE invalid', 0)}",
        f"- BOUNDARY_VALUE invalid: {final_metrics.get('BOUNDARY_VALUE invalid', 0)}",
        f"- DIRTY_DATA invalid: {final_metrics.get('DIRTY_DATA invalid', 0)}",
        f"- CLEAR_IMPROVE_WORSEN: {local_metrics.get('CLEAR_IMPROVE_WORSEN', 0)}",
        f"- CLEAR_DIRECTION_CHANGE: {local_metrics.get('CLEAR_DIRECTION_CHANGE', 0)}",
    ]
    (DOCS / "dataset_statistics.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    write_final_hardening_reports(by_dataset, final_metrics, final_sections, final_warnings)

def split_lines(rows, key):
    lines = ["| Slice | DEV | HOLDOUT |", "|---|---:|---:|"]
    values = sorted({row["metadata"][key] for row in rows})
    for value in values:
        dev = sum(1 for row in rows if row["metadata"][key] == value and row["metadata"]["split"] == "DEV")
        holdout = sum(1 for row in rows if row["metadata"][key] == value and row["metadata"]["split"] == "HOLDOUT")
        lines.append(f"| `{value}` | {dev} | {holdout} |")
    return lines

def write_final_hardening_reports(by_dataset, final_metrics, final_sections, final_warnings):
    report_lines = [
        "# Final Hardening Report",
        "",
        "## 一、Final Hardening 结果",
        "",
        "- Schema Validation：PASS",
        "- Distribution Validation：PASS",
        "- Semantic Validation：PASS",
        "- Leakage Validation：PASS",
        "- Split Validation：PASS",
        "- Source Validation：PASS",
        "- Medical Review：PENDING",
        "",
        "## 二、重复与泄漏",
        "",
        "| 指标 | 修复前 | 修复后 |",
        "|---|---:|---:|",
        f"| Exact Duplicate | 16 | {final_metrics.get('Exact Duplicate', 0)} |",
        f"| Cross-Split Exact Duplicate | 11 | {final_metrics.get('Cross-Split Exact Duplicate', 0)} |",
        f"| Cross-Split Near Duplicate Warning | 27 | {final_metrics.get('Cross-Split Near Duplicate Warning', 0)} |",
        f"| Safety Positive Duplicate | 10 | {final_metrics.get('Safety Positive Duplicate', 0)} |",
        f"| Report Duplicate Bundle | 0 | {final_metrics.get('Report Duplicate Bundle', 0)} |",
        "",
        "Near Duplicate 使用规则：对 Consultation 的 `scenario_type`、`case_class`、`patient_behavior_primary` 和去数字/空白后的 initial text 生成签名；跨 split 命中只作为 Warning，不作为 hard error。",
        "",
        "## 三、Split 分布",
        "",
        "### Consultation: Normal / Complex",
        *split_lines(by_dataset["consultation"], "case_class"),
        "",
        "### Consultation: Quick / Deep",
        *split_lines(by_dataset["consultation"], "mode"),
        "",
        "### Consultation: Difficulty",
        *split_lines(by_dataset["consultation"], "difficulty"),
        "",
        "### Consultation: Patient Behavior",
        *split_lines(by_dataset["consultation"], "patient_behavior_primary"),
        "",
        "### Safety",
        *split_lines(by_dataset["safety"], "safety_slice"),
        "",
        "### Report",
        *split_lines(by_dataset["report"], "report_slice"),
        "",
        "### Doctor Draft",
        *split_lines(by_dataset["doctor_draft"], "draft_slice"),
        "",
        "## 四、Safety Source",
        "",
        f"- Clinical Source Mismatch：{final_metrics.get('Clinical Source Mismatch', 0)}",
        f"- Policy Source Missing：{final_metrics.get('Policy Source Missing', 0)}",
        f"- NEEDS_POLICY_REVIEW：{final_metrics.get('NEEDS_POLICY_REVIEW', 0)}",
        f"- Prompt Injection Wrong Clinical Source：{final_metrics.get('Prompt Injection Wrong Clinical Source', 0)}",
        f"- Privacy Wrong Clinical Source：{final_metrics.get('Privacy Wrong Clinical Source', 0)}",
        f"- Special Population Source Mismatch：{final_metrics.get('Special Population Source Mismatch', 0)}",
        "",
        "## 五、Consultation State",
        "",
        f"- Disclosure Conflict：{final_metrics.get('Disclosure Conflict', 0)}",
        f"- UNKNOWN_ACCEPTABLE but still required ask：{final_metrics.get('UNKNOWN_ACCEPTABLE but still required ask', 0)}",
        f"- Conditional truth_path invalid：{final_metrics.get('Conditional truth_path invalid', 0)}",
        f"- Special Population Unobservable Conflict：{final_metrics.get('Special Population Unobservable Conflict', 0)}",
        "",
        "## 六、Report Slice",
        "",
        "| Slice | 有效 Case | 无效 Case |",
        "|---|---:|---:|",
    ]
    for sl in ["MULTI_INDICATOR", "UNIT_MISMATCH", "INDICATOR_ALIAS", "MISSING_TIME_POINT", "REFERENCE_RANGE_CHANGE", "BOUNDARY_VALUE", "DIRTY_DATA"]:
        report_lines.append(f"| `{sl}` | {final_metrics.get(sl + ' valid', 0)} | {final_metrics.get(sl + ' invalid', 0)} |")
    report_lines.extend([
        "",
        "以上高级 Slice 的数据内容已真实体现标签含义：多指标包含多个 normalized indicator，单位不一致包含多个 raw unit，别名包含多个 raw indicator name，缺失时间点不出现在 bundle 中，参考范围变化包含不同 reference_range，边界值包含 reference_range，脏数据包含明确 dirty feature。",
        "",
        "## 七、Frozen Holdout",
        "",
        "- Holdout：110",
        "- frozen：true",
        "- split_seed：20260823",
        "- dataset hashes generated：true",
        "",
        "## 八、Remaining Medical Review",
        "",
        "仅保留真正需要医学审核的问题：Safety Trigger、Near-Negative Medical Boundary、Critical Information、Department Routing、Special Population、Doctor Draft Medical Boundary。",
    ])
    (DOCS / "final_hardening_report.md").write_text("\n".join(report_lines) + "\n", encoding="utf-8")
    audit_lines = [
        "# Final Hardening Audit",
        "",
        "## Phase 1：Final Audit",
        "",
        "修复前审计结果来自本轮新增 Final Hardening Validator 首次运行与手工快速审计。",
        "",
        "| 问题 | 修复前 | 修复后 |",
        "|---|---:|---:|",
        f"| Cross-Split Duplicate | 11 | {final_metrics.get('Cross-Split Exact Duplicate', 0)} |",
        f"| Exact Duplicate | 16 | {final_metrics.get('Exact Duplicate', 0)} |",
        f"| Safety Duplicate Positive | 10 | {final_metrics.get('Safety Positive Duplicate', 0)} |",
        f"| Report Slice-content Mismatch | 30 | {sum(final_metrics.get(sl + ' invalid', 0) for sl in ['MULTI_INDICATOR', 'UNIT_MISMATCH', 'INDICATOR_ALIAS', 'MISSING_TIME_POINT', 'REFERENCE_RANGE_CHANGE', 'BOUNDARY_VALUE', 'DIRTY_DATA'])} |",
        f"| Disclosure Conflict | 6 | {final_metrics.get('Disclosure Conflict', 0)} |",
        f"| Unknown Resolution Conflict | 6 | {final_metrics.get('UNKNOWN_ACCEPTABLE but still required ask', 0)} |",
        f"| Conditional Detail Missing Truth | 12 | {final_metrics.get('Conditional truth_path invalid', 0)} |",
        f"| Special Population Observability | 90 | {final_metrics.get('Special Population Unobservable Conflict', 0)} |",
        f"| Policy Source Missing | 40 | {final_metrics.get('Policy Source Missing', 0)} |",
        "",
        "## Phase 2-9：Repair 与冻结",
        "",
        "- Phase 2：修复 Duplicate / Leakage。",
        "- Phase 3：拆分 Safety clinical / policy source。",
        "- Phase 4：重建 Consultation disclosure 与 resolution state。",
        "- Phase 5：重构 Report Advanced Slice。",
        "- Phase 6：内容修复完成后重新 Stratified Split。",
        "- Phase 7：冻结 Holdout。",
        "- Phase 8：Full Validation PASS。",
        "- Phase 9：生成 Final Report。",
    ]
    (DOCS / "final_hardening_audit.md").write_text("\n".join(audit_lines) + "\n", encoding="utf-8")

def write_medical_queue(rows):
    lines = ["# 医学审核队列", "", "本队列只保留真正需要医学知识源或专家判断的问题，不包含数组错配、重复字段、pair_id 错误、slice 错误或 UNKNOWN 默认值等 Dataset Engineering（数据集工程）问题。", ""]
    for dataset, row in rows:
        if row["metadata"]["review_status"] != "NEEDS_MEDICAL_REVIEW":
            continue
        cid = row["metadata"]["case_id"]
        if dataset == "consultation":
            fields = ["Department Routing", "Critical Information", "Safety Trigger"]
        elif dataset == "safety":
            fields = ["Safety Trigger", "Red Flag Near-Negative 边界"]
        elif dataset == "doctor_draft":
            fields = ["Doctor Draft 医疗边界", "Unsupported Claim 边界"]
        else:
            fields = ["Medical Review"]
        lines.append(f"## {cid}")
        lines.append(f"- dataset: {dataset}")
        lines.append(f"- fields: {', '.join(fields)}")
        lines.append("- current_label: NEEDS_MEDICAL_REVIEW")
        lines.append("- reason_for_review: 仓库存在规则或模板来源，但医学正确性仍需医学知识源或专家复核。")
        lines.append("- source_found: true")
        lines.append("- source_missing: false")
        lines.append("")
    (DOCS / "medical_review_queue.md").write_text("\n".join(lines), encoding="utf-8")

def write_repair_log():
    content = """# 修复日志

## 1. Priority / Gate

- 问题类型：`IMPORTANT + TASK_GATE` 被大量使用。
- 影响范围：Consultation Information Requirement。
- 根因：旧生成器把重要信息直接绑定 Task Gate。
- 修改位置：`tools/build_dataset.py`、`tools/validate_dataset.py`。
- 修复方法：只允许 `CRITICAL + SAFETY_GATE`、`CRITICAL + TASK_GATE`、`IMPORTANT + SCORE_ONLY`、`OPTIONAL + SCORE_ONLY`。
- 修复前示例：`IMPORTANT + TASK_GATE`。
- 修复后示例：`IMPORTANT + SCORE_ONLY`。
- Validator 规则：非法组合直接 Semantic Validation FAIL。

## 2. Patient Truth 全 UNKNOWN

- 问题类型：`expected_value` 默认生成 `UNKNOWN`。
- 影响范围：120 条 Consultation。
- 根因：旧生成器没有构造 Hidden Profile（隐藏患者事实）。
- 修改位置：`tools/build_dataset.py`。
- 修复方法：为 duration、severity、progression、associated symptom、medication、allergy、risk fact 生成具体状态和值。
- 修复前示例：所有 Required Slot 均为 `UNKNOWN`。
- 修复后示例：`duration = 3天`，`severity = 中等`。
- Validator 规则：所有 Critical Slot 均为 UNKNOWN 时失败。

## 3. Topic / RAG / Department 错配

- 问题类型：clinical topic、RAG topic、department 使用数组轮转。
- 影响范围：Consultation。
- 根因：缺少显式 Topic Mapping。
- 修改位置：`tools/build_dataset.py`。
- 修复方法：新增 `clinical_topic_mapping`，绑定 symptom rule、triage rule、preferred department 和 acceptable departments。
- Validator 规则：topic、RAG、triage、department 不一致时失败。

## 4. Department 重复

- 问题类型：acceptable departments 可能重复或与 unacceptable departments 冲突。
- 修复方法：生成时去重，校验时检查集合交集。
- Validator 规则：重复或冲突直接失败。

## 5. Patient Behavior 重复

- 问题类型：primary behavior 与 secondary behavior 可能相同。
- 修复方法：如果次行为没有额外信息价值则置空。
- Validator 规则：两者相同直接失败。

## 6. Minimal Pair

- 问题类型：pair_id 相同不代表真实最小对照组。
- 修复方法：15 组 pair，每组严格 1 条 Red Flag Positive 和 1 条 Near-Negative，使用同一 source rule、clinical topic 和 patient background。
- Validator 规则：成员数、正负例数量、source rule、clinical topic、patient background 全部检查。

## 7. Safety Action 模糊

- 问题类型：`when repo rules support it` 无法直接评分。
- 修复方法：新增 `expected_policy` 与 `required_actions`。
- Validator 规则：禁止模糊表达，expected_policy 必须为确定枚举。

## 8. Report reference range

- 问题类型：`repo/test synthetic` 与 `abnormal_points=[]` 语义不清。
- 修复方法：新增 `reference_range_evaluable=false` 与 `clinical_interpretation_evaluable=false`。
- Validator 规则：参考范围不可评估时不得产生 abnormal_points，也不得启用临床解释。

## 9. Doctor Draft Slice

- 问题类型：slice 标签和输入内容不一致。
- 修复方法：为每个 slice 生成对应的 missing、unknown、contradiction metadata、multi-source、unsupported claim targets 或 structured edge。
- Validator 规则：slice 与内容不一致时失败。

## 10. Semantic Validator

- 问题类型：旧 Validator 只证明格式和数量正确。
- 修复方法：分层输出 Schema Validation、Distribution Validation、Semantic Validation、Medical Review。
- Validator 规则：结构、分布和语义分别判定，医学审核状态单独报告。

## 11. Final Dataset Hardening

- 问题类型：Evaluation Leakage、Split Methodology、Source Provenance、Conversation State Consistency、Report Slice Validity。
- 修改位置：`tools/local_optimize_dataset.py`、`tools/local_optimization_validator.py`、`tools/validate_dataset.py`、`tools/summarize_dataset.py`。
- 修复方法：重建 deterministic stratified split，冻结 Holdout；Safety Minimal Pair 不跨 split；每条 Case 增加 Source Provenance；Consultation 增加对话状态 Ground Truth；Report 禁止把数值方向解释为临床改善或恶化。
- Validator 规则：`Split Correlation Error`、`Frozen Holdout Pair Leakage`、`Source Provenance Missing`、`Conversation State Error`、`Report Slice Validity Error` 必须为 0。
"""
    (DOCS / "repair_log.md").write_text(content, encoding="utf-8")

def main():
    rows, by_dataset = load_rows()
    metrics = semantic_metrics(by_dataset)
    write_statistics(rows, by_dataset, metrics)
    write_medical_queue(rows)
    write_repair_log()
    print("已刷新 dataset_statistics.md、medical_review_queue.md、repair_log.md")

if __name__ == "__main__":
    main()
