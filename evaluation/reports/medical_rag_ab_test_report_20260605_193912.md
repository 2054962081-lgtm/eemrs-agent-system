# Medical RAG A/B Test Report

## 样例整理结果
- 历史测试文件数量：30
- 去重前 case 数：112
- 去重后唯一 case 数：90
- 重复 case 数：22
- 无历史无 RAG 分数 case 数：0

## RAG 测试环境
- rag.enabled：configured true by default/env
- RAG 服务可访问：None
- Milvus collection 存在：None
- num_entities：None
- doc_type counts：None

## 总体对比
- 无 RAG 平均总分：7.8511
- 有 RAG 平均总分：8.3344
- 平均分变化：0.4833
- 无 RAG 必问命中率：0.3663
- 有 RAG 必问命中率：0.4421
- 必问命中率变化：0.0758
- 改善 case 数：33
- 退步 case 数：34
- 持平 case 数：23
- 跳过已完成 case 数：90
- 本次新执行 case 数：0
- 重试失败 case 数：0

## 分组结论
- anti_misleading: cases=6, no_rag_avg=4.8667, rag_avg=9.0, delta=4.1333
- record_generation: cases=5, no_rag_avg=5.7, rag_avg=4.1, delta=-1.6
- special_population: cases=5, no_rag_avg=4.42, rag_avg=8.82, delta=4.4
- 儿童专项: cases=10, no_rag_avg=8.75, rag_avg=8.43, delta=-0.32
- 免疫低下/肿瘤/术后专项: cases=5, no_rag_avg=8.74, rag_avg=9.24, delta=0.5
- 其他高风险疾病扩展: cases=4, no_rag_avg=8.85, rag_avg=8.5, delta=-0.35
- 孕产妇专项: cases=8, no_rag_avg=9.075, rag_avg=9.15, delta=0.075
- 快速问诊普通: cases=10, no_rag_avg=6.77, rag_avg=8.73, delta=1.96
- 慢病用药专项: cases=10, no_rag_avg=8.61, rag_avg=8.27, delta=-0.34
- 深度问诊: cases=8, no_rag_avg=8.6625, rag_avg=8.825, delta=0.1625
- 精神心理专项: cases=5, no_rag_avg=8.46, rag_avg=8.4, delta=-0.06
- 红旗风险: cases=6, no_rag_avg=8.8333, rag_avg=8.5, delta=-0.3333
- 老年人专项: cases=8, no_rag_avg=8.65, rag_avg=7.525, delta=-1.125

## 注意事项
- 本次以 no-rag-only 运行：只整理历史无 RAG 样例和分数，不调用当前 RAG 后端。
- 断点续跑已读取缓存来源：evaluation\reports\rag_ab_test_progress.json
