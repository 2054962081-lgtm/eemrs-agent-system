# Medical RAG A/B Test Report

## 样例整理结果
- 历史测试文件数量：30
- 去重前 case 数：112
- 去重后唯一 case 数：90
- 重复 case 数：22
- 无历史无 RAG 分数 case 数：0

## RAG 测试环境
- rag.enabled：configured true by default/env
- RAG 服务可访问：True
- Milvus collection 存在：True
- num_entities：62
- doc_type counts：{'medical_record_template': 12, 'special_population': 12, 'red_flag': 13, 'symptom_inquiry': 14, 'department_triage': 11}

## 总体对比
- 无 RAG 平均总分：7.8511
- 有 RAG 平均总分：8.7411
- 平均分变化：0.89
- 无 RAG 必问命中率：0.3663
- 有 RAG 必问命中率：0.62
- 必问命中率变化：0.2537
- 改善 case 数：61
- 退步 case 数：15
- 持平 case 数：14
- 跳过已完成 case 数：0
- 本次新执行 case 数：90
- 重试失败 case 数：0

## 分组结论
- anti_misleading: cases=6, no_rag_avg=4.8667, rag_avg=8.5667, delta=3.7
- record_generation: cases=5, no_rag_avg=5.7, rag_avg=4.1, delta=-1.6
- special_population: cases=5, no_rag_avg=4.42, rag_avg=8.48, delta=4.06
- 儿童专项: cases=10, no_rag_avg=8.75, rag_avg=9.24, delta=0.49
- 免疫低下/肿瘤/术后专项: cases=5, no_rag_avg=8.74, rag_avg=9.56, delta=0.82
- 其他高风险疾病扩展: cases=4, no_rag_avg=8.85, rag_avg=9.3, delta=0.45
- 孕产妇专项: cases=8, no_rag_avg=9.075, rag_avg=8.875, delta=-0.2
- 快速问诊普通: cases=10, no_rag_avg=6.77, rag_avg=9.14, delta=2.37
- 慢病用药专项: cases=10, no_rag_avg=8.61, rag_avg=8.87, delta=0.26
- 深度问诊: cases=8, no_rag_avg=8.6625, rag_avg=8.9875, delta=0.325
- 精神心理专项: cases=5, no_rag_avg=8.46, rag_avg=9.44, delta=0.98
- 红旗风险: cases=6, no_rag_avg=8.8333, rag_avg=9.2, delta=0.3667
- 老年人专项: cases=8, no_rag_avg=8.65, rag_avg=8.7, delta=0.05

## 注意事项
- --rerun-rag enabled: historical no-RAG scores are reused, current RAG responses are requested again.
