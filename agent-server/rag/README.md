# RAG 知识库入库脚本

本目录用于医疗智能体第一阶段 RAG 的本地知识库校验、chunk 构建、Milvus 入库、重建和检索验证。

当前阶段只完成知识入库能力，不修改患者预问诊主流程，不修改前端页面，也不接入正式 RAG 生成链路。

## Milvus 要求

默认连接：

```text
localhost:19530
```

如果无法连接，请先确认 Docker 容器已启动：

```bash
docker ps
```

并确认 `milvus-standalone` 暴露端口 `19530`。

Milvus 数据实际落盘位置取决于 `docker-compose.yml` 的 `volumes` 配置。脚本不会修改 Docker Desktop 设置。

如果配置为：

```yaml
./volumes/milvus:/var/lib/milvus
```

数据会写入当前 `docker-compose.yml` 所在目录下的 `volumes/milvus`。

如果 `docker-compose.yml` 位于 D 盘，例如：

```text
D:\medical-rag-docker\milvus\docker-compose.yml
```

则数据会写入：

```text
D:\medical-rag-docker\milvus\volumes\milvus
```

如果使用 Docker named volume 或 Docker Desktop 默认存储，数据可能进入 Docker Desktop 的 WSL2 磁盘镜像，通常在 C 盘用户目录下。

## 安装依赖

```bash
pip install -r requirements-rag.txt
```

默认本地 embedding 模型：

```text
BAAI/bge-small-zh-v1.5
```

如果模型无法加载，请确认 `sentence-transformers` 已安装，并且模型可下载或已缓存到本地。

## 配置

可通过环境变量覆盖：

- `MILVUS_HOST`
- `MILVUS_PORT`
- `MILVUS_COLLECTION_NAME`
- `KNOWLEDGE_BASE_DIR`
- `EMBEDDING_PROVIDER`
- `EMBEDDING_MODEL_NAME`

不要提交 API key、`.env` 或真实患者数据。

## 校验知识库

```bash
python -m rag.validate_rag_knowledge
```

## dry-run

只校验 JSON 并构建 chunk，不连接 Milvus，不写入数据：

```bash
python -m rag.ingest_to_milvus --dry-run
```

## 重建并入库

```bash
python -m rag.ingest_to_milvus --reset
```

也可以单独重建 collection：

```bash
python -m rag.reset_collection --yes
```

## 检索测试

```bash
python -m rag.search_rag --query "我家宝宝8个月发烧39度不吃奶" --top-k 8
python -m rag.search_rag --query "我爸72岁胸口闷还冒汗" --top-k 8
python -m rag.search_rag --query "我怀孕32周头痛眼花腿肿" --top-k 8
```

## 启动本地 RAG HTTP 服务

SpringBoot 后端接入 RAG 时默认调用本地 HTTP 服务：

```bash
python -m rag.rag_api_server
```

或：

```bash
uvicorn rag.rag_api_server:app --host 0.0.0.0 --port 18080
```

验证：

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:18080/rag/retrieve" `
  -ContentType "application/json" `
  -Body '{"query":"我爸72岁胸口闷还冒汗","top_k":8,"scene":"pre_inquiry"}'
```

RAG HTTP 服务只做检索，不重新入库，不删除 collection，不返回 embedding 或完整 `content_json`。

## 安全说明

- 本阶段不替代医生诊断。
- 不伪造具体医学指南来源。
- 不生成药物处方或剂量。
- 不写入真实患者姓名、身份证号、手机号、住址、病历号等隐私信息。
- 不调用 DeepSeek 生成 embedding；当前仅使用本地 sentence-transformers。
