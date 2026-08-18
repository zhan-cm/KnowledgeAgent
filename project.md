# 企业知识库问答 Agent（RAG Agent）定制化落地方案（本机版）

> 本方案根据你的实际环境（16GB 内存、8G 显存、Windows 11、Docker Desktop、JDK 17、Maven、Python 3.10、DeepSeek API Key）和选型（pgvector + 本地文件存储 + Redis Streams + Spring Boot 3 + FastAPI）重新生成，聚焦 **MVP 快速跑通**，兼顾后续扩展。

------

## 一、项目定位与核心目标

**产品名称**：`KnowledgeAgent`
**核心价值**：让企业员工通过自然语言对话，快速获取分散在内部文档（PDF、Word、TXT）中的知识，并保证回答可溯源、操作可审计。

**本机约束与目标**：

- 开发环境：16GB 内存、8G 显存，需要轻量化组件，避免资源耗尽。
- 目标：**2~4 周内跑通最小闭环**，通过 Postman 验证完整链路。
- 非功能性要求：单租户、单机部署即可；回答延迟 < 5 秒（含检索与 LLM 生成）。

------

## 二、MVP 功能范围（明确边界）

**包含**：

- 用户认证（JWT，注册/登录）
- 知识库管理（创建、查看知识库）
- 文档上传（PDF/Word/TXT，保存到本地磁盘）
- 文档解析、分块、向量化、存入 pgvector（异步处理）
- 单轮问答：输入问题，返回答案 + 引用来源（文档名、片段内容）
- 简单的权限控制：登录用户可访问所有文档（预留扩展接口）
- 审计日志：记录问答与文档操作

**不包含（后续迭代）**：

- 多租户、文档级权限
- 多轮对话上下文、流式输出
- 批量导入、反馈收集、管理后台统计
- 前端界面

------

## 三、技术架构与选型

### 总体架构图

text

```
┌──────────────────────────────────────────────────────────┐
│                    Postman / curl                        │
└────────────────────────┬─────────────────────────────────┘
                         │ REST
┌────────────────────────▼─────────────────────────────────┐
│              Java 后端 (Spring Boot 3.2)                 │
│  认证授权 · 知识库/文档管理 · 对话管理 · 审计日志          │
│  AiClient (WebClient) · Redis Streams 生产者             │
└───────┬──────────────────────┬───────────────────────────┘
        │                      │ 调用 /query
        │                      ▼
        │            ┌──────────────────┐
        │            │ Python AI 服务   │
        │            │ (FastAPI)        │
        │            │ 解析/分块/向量化 │
        │            │ 检索/LLM 生成    │
        │            └──────┬───────────┘
        │                   │
        ▼                   ▼
┌───────────────┐   ┌──────────────────┐   ┌───────────────┐
│ PostgreSQL 16 │   │ Redis 7          │   │ 本地文件系统  │
│ + pgvector    │   │ (Streams 队列)   │   │ data/docs     │
└───────────────┘   └──────────────────┘   └───────────────┘
```



### 技术选型表

| 层次       | 选型                             | 版本/说明                                    | 本机适配理由                                         |
| :--------- | :------------------------------- | :------------------------------------------- | :--------------------------------------------------- |
| 关系数据库 | PostgreSQL + pgvector            | pgvector/pgvector:pg16                       | 复用业务库，减少独立服务，pgvector 满足 MVP 向量检索 |
| 缓存/队列  | Redis                            | redis:7-alpine                               | 使用 Streams 实现异步索引任务，轻量                  |
| 文件存储   | 本地文件系统                     | `data/documents/`                            | 开发简单，生产可换 MinIO/S3（接口保持一致）          |
| Java 后端  | Spring Boot 3.2 + JDK 17 + Maven | 本机运行，不容器化                           | 便于调试，节省 Docker 资源                           |
| Python AI  | FastAPI + Python 3.10 + venv     | 本机运行                                     | 同上                                                 |
| Embedding  | `BAAI/bge-small-zh`              | sentence-transformers 本地加载，向量维度 512 | 8G 显存轻松运行，免费离线，中文效果好                |
| LLM        | DeepSeek API                     | OpenAI 兼容接口                              | 你有 API Key，国内访问快，成本低                     |
| 前端       | 无                               | Postman 测试                                 | 聚焦后端与 AI                                        |
| 部署       | Docker Compose 仅跑 PG + Redis   | 后端与 AI 本机运行                           | 节省内存，开发调试方便                               |

------

## 四、项目目录结构

text

```
knowledge-agent/
├── docker-compose.yml              # 仅启动 PostgreSQL+pgvector 和 Redis
├── backend/                        # Java 后端（Spring Boot）
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/knowledgeagent/
│       │   ├── KnowledgeAgentApplication.java
│       │   ├── config/             # Security、WebClient、Redis、CORS 等
│       │   ├── controller/         # Auth、KnowledgeBase、Document、Conversation、Internal
│       │   ├── service/            # 业务逻辑
│       │   ├── repository/         # Spring Data JPA 接口
│       │   ├── entity/             # User、KnowledgeBase、Document、Conversation、Message、AuditLog
│       │   ├── dto/                # 请求/响应对象
│       │   ├── exception/          # 全局异常处理
│       │   ├── ai/                 # AiClient（调用 Python）
│       │   └── security/           # JWT 过滤器、UserDetailsService
│       └── main/resources/
│           ├── application.yml
│           └── db/migration/       # Flyway SQL（V1__init.sql）
├── ai-service/                     # Python AI 服务（FastAPI）
│   ├── app/
│   │   ├── main.py                 # FastAPI 入口
│   │   ├── api/                    # ingest.py、query.py、internal.py
│   │   ├── core/                   # config.py、parsing.py、chunking.py、embedding.py、retrieval.py、llm.py
│   │   ├── models/                 # Pydantic 模型
│   │   └── utils/
│   ├── requirements.txt
│   ├── .env.example
│   └── .gitignore
├── data/
│   └── documents/                  # 上传的原始文档（本地存储）
└── README.md
```



------

## 五、数据库设计（PostgreSQL + pgvector）

使用 Flyway 管理数据库迁移，`V1__init.sql` 包含以下表和扩展：

sql

```
CREATE EXTENSION IF NOT EXISTS vector;

-- 用户表
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 知识库表
CREATE TABLE knowledge_bases (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 文档表
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    kb_id BIGINT NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,          -- 相对路径 data/documents/xxx.pdf
    file_type VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING/INDEXING/INDEXED/FAILED
    version INT DEFAULT 1,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 对话表（MVP 单轮可暂不用，但保留结构）
CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    kb_id BIGINT REFERENCES knowledge_bases(id),
    title VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 消息表（记录问答历史，便于扩展多轮）
CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,                -- user/assistant
    content TEXT NOT NULL,
    citations JSONB,                          -- 引用片段 [{documentId, title, chunkText, page}]
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 审计日志表
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id BIGINT,
    detail TEXT,
    ip VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 文档分块表（存储向量和文本块）
CREATE TABLE document_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    kb_id BIGINT NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    chunk_text TEXT NOT NULL,
    chunk_index INT NOT NULL,
    page_number INT,
    metadata JSONB,
    embedding vector(512)                     -- bge-small-zh 输出 512 维
);

-- 向量索引（余弦相似度）
CREATE INDEX ON document_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```



> **说明**：MVP 单租户，但 `users` 和 `knowledge_bases` 已预留扩展字段；`conversations` 和 `messages` 表结构保留，便于后续实现多轮对话。

------

## 六、Java 后端设计

### 1. 模块划分

text

```
com.example.knowledgeagent
├── auth          # 认证与授权（JWT）
├── user          # 用户管理
├── kb            # 知识库管理
├── document      # 文档管理
├── conversation  # 对话管理（MVP 仅创建会话和发送消息）
├── audit         # 审计日志
├── ai            # 调用 Python AI 服务
└── config        # 全局配置
```



### 2. 核心 API 设计

**认证相关**

- `POST /api/auth/register` - 注册用户
- `POST /api/auth/login` - 登录，返回 JWT

**知识库相关**

- `GET /api/kbs` - 获取知识库列表
- `POST /api/kbs` - 创建知识库
- `GET /api/kbs/{kbId}` - 知识库详情
- `DELETE /api/kbs/{kbId}` - 删除知识库

**文档相关**

- `POST /api/documents/upload` - 上传文档（multipart，参数：file, kbId）
- `GET /api/documents/{docId}` - 获取文档元数据
- `DELETE /api/documents/{docId}` - 删除文档
- `GET /api/documents/{docId}/preview` - 获取原文内容（可选）
- `POST /api/documents/{docId}/reindex` - 重新索引（可选）

**问答相关**

- `POST /api/conversations` - 创建新对话（参数：kbId）
- `POST /api/conversations/{conversationId}/messages` - 发送消息（参数：question）
- `GET /api/conversations/{conversationId}/messages` - 获取历史消息

**内部回调（供 Python 调用）**

- `PUT /internal/documents/{docId}/status` - 更新文档索引状态（需内部 token）

### 3. 关键流程：文档上传与索引

1. 用户上传文件，Java 将文件保存到 `data/documents/{uuid}.{ext}`，获取相对路径。
2. 在 `documents` 表插入记录，状态为 `PENDING`。
3. 构造消息 `{"documentId": 1, "filePath": "data/documents/xxx.pdf", "kbId": 1}`，发送到 Redis Stream `index-requests`。
4. Python 服务监听该 Stream，消费后执行解析、分块、向量化，写入 `document_chunks`。
5. Python 完成后回调 `PUT /internal/documents/{docId}/status`，更新状态为 `INDEXED` 或 `FAILED`。
6. 用户可在前端查询文档状态，确认是否可提问。

### 4. AiClient（调用 Python /query）

使用 Spring WebClient：

java

```
@Service
public class AiClient {
    private final WebClient webClient;

    public AiClient(@Value("${ai.service.base-url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public QueryResponse query(QueryRequest request) {
        return webClient.post()
                .uri("/query")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(QueryResponse.class)
                .block();
    }
}
```



`QueryRequest` 包含：`question`, `kbIds`, `allowedDocumentIds`（MVP 可传空，表示不过滤），`topK`。
`QueryResponse` 包含：`answer`, `citations`（列表，每个含 `documentId`, `title`, `chunkText`, `page`）。

------

## 七、Python AI 服务设计

### 1. 模块划分

text

```
ai-service/
├── app/
│   ├── main.py                 # FastAPI 实例
│   ├── api/ingest.py           # 文档摄入接口（HTTP，也可被 Stream 消费者调用）
│   ├── api/query.py            # 问答接口
│   ├── api/internal.py         # 内部接口（如健康检查）
│   ├── core/config.py          # 配置加载（.env）
│   ├── core/parsing.py         # 解析 PDF/Word/TXT
│   ├── core/chunking.py        # 文本分块
│   ├── core/embedding.py       # 向量化
│   ├── core/retrieval.py       # pgvector 检索
│   ├── core/llm.py             # DeepSeek API 调用
│   └── models/schemas.py       # Pydantic 模型
├── requirements.txt
└── .env.example
```



### 2. RAG 流程

#### 文档摄入（由 Redis Streams 消费者触发）

1. 从 Redis Stream `index-requests` 读取消息，获取 `document_id`, `file_path`, `kb_id`。
2. 根据文件扩展名调用 `parsing.py` 提取文本（PDF 用 PyMuPDF，Word 用 python-docx，TXT 直接读取）。
3. 文本清洗：去除空行、特殊字符，保留基本段落结构。
4. 分块：固定长度 500 字符，重叠 50 字符，记录 `chunk_index` 和页码。
5. 调用 `embedding.py` 加载 `BAAI/bge-small-zh` 模型，将每个块转为 512 维向量。
6. 将向量和元数据插入 PostgreSQL 的 `document_chunks` 表（使用 `psycopg2` 或 SQLAlchemy）。
7. 回调 Java：`PUT http://localhost:8080/internal/documents/{document_id}/status`，携带内部 token，更新状态为 `INDEXED`。
8. 如果异常，记录日志并回调状态为 `FAILED`，附错误信息。

#### 问答（`POST /query`）

1. 接收 `question`, `kb_ids`, `allowed_document_ids`, `top_k`。

2. 将问题用同一 Embedding 模型转为向量。

3. 在 `document_chunks` 表中执行相似度查询（余弦相似度），条件：

   - `kb_id IN kb_ids`
   - 如果 `allowed_document_ids` 非空，则 `document_id IN allowed_document_ids`
   - 按相似度降序取前 `top_k` 条。

4. 构建 Prompt：

   text

   ```
   你是一个企业知识库助手。请根据以下参考资料回答用户问题。
   如果资料中没有答案，请如实说明。
   
   参考资料：
   [1] {chunk1}
   [2] {chunk2}
   ...
   
   用户问题：{question}
   回答（请引用资料编号）：
   ```

   

5. 调用 DeepSeek API（OpenAI 兼容格式），返回答案。

6. 将答案和引用的片段元数据（`document_id`, `title`, `chunk_text`, `page`）一起返回给 Java。

### 3. API 设计

- `POST /query`：请求体如 `{"question": "...", "kb_ids": [1], "allowed_document_ids": [], "top_k": 5}`，响应 `{"answer": "...", "citations": [...]}`
- `POST /ingest`：备用的同步摄入接口，便于测试（可不用，直接走 Redis Streams）
- `GET /health`：健康检查

### 4. 与 Java 的通信

- **同步**：Java 通过 WebClient 调用 `/query`，等待响应。
- **异步索引**：Java 发送消息到 Redis Streams，Python 后台消费者处理，完成后回调 Java。

------

## 八、多轮对话与上下文管理（预留设计）

MVP 阶段仅实现单轮问答，但数据库已保存对话和消息，后续可轻松扩展：

- Java 在发送提问时，从 `messages` 表获取最近 N 轮对话，作为 `conversation_history` 传给 Python。
- Python 结合历史进行查询改写，提升多轮准确率。
- 支持流式输出时，Java 可使用 WebFlux 或 SSE 转发 Python 的流式响应。

------

## 九、权限与安全

- **认证**：使用 Spring Security + JWT，密码 BCrypt 加密。
- **授权**：MVP 所有登录用户可访问所有知识库和文档，但接口设计已预留 `allowed_document_ids` 参数，未来可增加文档级权限，由 Java 在调用 Python 前计算出允许的文档 ID 列表。
- **内部接口保护**：Python 回调 Java 的 `/internal/**` 接口时，需携带固定 token（配置在环境变量中），防止外部访问。
- **文件上传限制**：限制文件大小 20MB，类型仅 PDF/Word/TXT。
- **审计日志**：记录登录、上传、提问、删除等操作。

------

## 十、部署与启动步骤（本机）

### 1. 启动基础组件

创建 `docker-compose.yml`：

yaml

```
version: '3.8'
services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: knowledge-postgres
    environment:
      POSTGRES_DB: knowledge_agent
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin -d knowledge_agent"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: knowledge-redis
    ports:
      - "6379:6379"
    volumes:
      - redisdata:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
  redisdata:
```



执行：

bash

```
docker compose up -d
```



### 2. 启动 Java 后端

在 `backend/` 目录：

bash

```
mvn spring-boot:run
```



确保 `application.yml` 数据库连接指向 `localhost:5432`，Redis 指向 `localhost:6379`。

### 3. 启动 Python AI 服务

在 `ai-service/` 目录：

bash

```
python -m venv venv
venv\Scripts\activate   # Windows
pip install -r requirements.txt
copy .env.example .env  # 填入 DeepSeek API Key
uvicorn app.main:app --reload --port 8000
```



### 4. 测试流程（Postman）

1. 注册用户：`POST http://localhost:8080/api/auth/register`
2. 登录获取 JWT：`POST /api/auth/login`
3. 创建知识库：`POST /api/kbs`（Header 带 Bearer Token）
4. 上传文档：`POST /api/documents/upload`（form-data，file + kbId）
5. 等待几秒后查询文档状态：`GET /api/documents/{docId}`，确认状态为 `INDEXED`
6. 创建对话并发送消息：`POST /api/conversations`，然后 `POST /api/conversations/{id}/messages`
7. 查看返回的答案和引用来源

------

## 十一、迭代路线图

### MVP（当前方案，2~4 周）

- 单租户，用户认证
- 文档上传、解析、向量化（异步）
- 单轮问答，返回答案和引用
- 审计日志
- 使用 Postman 测试

### V1.1（完善企业功能）

- 多知识库权限（按用户/部门）
- 多轮对话上下文
- 流式输出（SSE）
- 文档级权限过滤（检索时传 `allowed_document_ids`）
- 批量导入与重新索引
- 反馈收集（点赞/点踩）

### V1.2（进阶优化）

- 混合检索（向量 + BM25）
- 重排序模型（CrossEncoder）
- 查询改写
- 管理后台统计报表
- 敏感词过滤

### V2.0（规模化）

- 支持更多数据源（Confluence、Notion、网页）
- 分布式任务队列（替换 Redis Streams 为 Kafka）
- 模型可配置（支持本地 LLM 或多家 API）
- 插件化工具调用
- 容器化部署到 Kubernetes

------

## 十二、风险与应对

| 风险                   | 应对措施                                                     |
| :--------------------- | :----------------------------------------------------------- |
| 本机内存不足（16GB）   | 只容器化 PostgreSQL 和 Redis；后端和 AI 服务本机运行；关闭不必要的 IDE 插件 |
| Embedding 模型下载失败 | 预先下载 `BAAI/bge-small-zh` 到本地，或使用国内镜像 Hugging Face |
| DeepSeek API 调用失败  | 增加重试机制（3次）；记录详细日志；准备降级方案（如本地 Ollama 备用） |
| PDF 解析质量差         | MVP 仅支持文本型 PDF，扫描件可后续接入 OCR                   |
| 向量检索准确率不高     | 调整分块大小和重叠；后续引入混合检索和重排序                 |
| 索引任务失败           | Redis Streams 消费组自动重试；记录失败原因；提供手动重新索引接口 |

------

## 十三、使用 AI 工具协同开发建议

1. **生成代码骨架**：用 Claude Code / ChatGPT 按模块生成代码，如“请为 Spring Boot 3.2 生成用户认证模块（User 实体、JwtUtil、SecurityConfig、AuthController）”。
2. **在 IDE 内辅助**：使用 IntelliJ 的 AI Assistant 或 Cursor 进行代码补全、解释和重构。
3. **调试报错**：将完整错误日志粘贴给 AI，快速定位问题。
4. **生成测试**：让 AI 为关键 Service 生成 JUnit 测试和 Python pytest 测试。
5. **审查代码**：定期让 AI 分析项目结构，提出优化建议。

