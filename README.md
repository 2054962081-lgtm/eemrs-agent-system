# EEMRS Agent System 👉 [在线查看医疗智能体项目作品集](https://2054962081-lgtm.github.io/eemrs-agent-system/)

面向电子医疗场景的 AI Agent 系统，集成智能预问诊、科室推荐与挂号、医疗报告分析、病历草稿生成、RAG 检索及分层记忆等能力。

## 项目结构

```text
eemrs-agent-system/
├── frontend-vue/          # Vue 前端
├── eemrs-server-master/   # 核心业务后端
├── agent-server/          # AI Agent 后端
├── rag/                   # RAG 检索服务
├── rag_knowledge/         # 医疗知识库
├── portfolio.html         # 项目作品集
└── README.md
```

## 启动项目

### 1. 启动业务后端

```bash
cd eemrs-server-master
mvn spring-boot:run
```

默认端口：

```text
http://localhost:8080
```

### 2. 启动 Agent 后端

```bash
cd agent-server
mvn spring-boot:run
```

### 3. 启动 RAG 服务

```bash
cd agent-server
python -m rag.rag_api_server
```

默认端口：

```text
http://localhost:18080
```

### 4. 启动前端

```bash
cd frontend-vue
npm install
npm run dev
```

浏览器访问终端显示的本地地址即可使用系统。

