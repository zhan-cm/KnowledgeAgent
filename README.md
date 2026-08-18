# KnowledgeAgent — 企业知识库问答 Agent（RAG）

基于文档问答的企业知识库 MVP：上传 PDF/Word/TXT → 异步解析分块向量化（pgvector）→ 自然语言提问，返回带引用的答案。

## 架构

```
Postman/curl
    │ REST (JWT)
┌───▼────────────────────────────────────────────┐
│ Spring Boot 3.2 后端（本仓库根目录，端口 8080）   │
│ 认证 · 知识库/文档管理 · 对话 · 审计 · AiClient   │
└───┬──────────────────────────┬─────────────────┘
    │ Redis Streams 异步索引    │ HTTP POST /query
┌───▼──────────────┐  ┌────────▼───────────────┐
│ Redis 7          │  │ FastAPI AI 服务（8000） │
│ index-requests   │  │ 解析/分块/向量化        │
└──────────────────┘  │ 检索/DeepSeek 生成      │
                      └────────┬───────────────┘
                  ┌────────────▼───────────────┐
                  │ PostgreSQL 16 + pgvector   │
                  └────────────────────────────┘
```

- 向量维度 512（`BAAI/bge-small-zh`），本地免费运行
- 文档存本地 `data/documents/`，数据库只存相对路径
- 索引流程：Java 存文件 → 发 Redis Stream 消息（含绝对路径）→ Python 消费解析入库 → 回调 Java 更新状态

## 快速启动

### 1. 启动基础组件（Docker）

```bash
docker compose up -d          # PostgreSQL(5432) + Redis(6380，容器内 6379)
```

> 本机 6379 已被 Windows 原生 Redis 占用，因此容器 Redis 映射到 6380。

### 2. 启动 Java 后端（项目根目录）

```bash
mvnw.cmd spring-boot:run      # Windows CMD；或 IDE 直接运行 KnowledgeAgentApplication
```

Flyway 会自动建表。首次启动前确认 `src/main/resources/application.yml` 中数据库连接与 docker-compose 一致。

### 3. 启动 Python AI 服务

```bash
cd ai-service
python -m venv venv
venv\Scripts\activate        # Windows
pip install -r requirements.txt
copy .env.example .env       # 填入 DEEPSEEK_API_KEY
uvicorn app.main:app --port 8000
```

- 首次启动会下载 `BAAI/bge-small-zh` 模型（约 100MB），国内可先 `set HF_ENDPOINT=https://hf-mirror.com`
- 8G 显存机器想用 GPU 加速可自行安装 CUDA 版 torch

### 4. Postman 验证链路

1. `POST /api/auth/register` — `{"username":"alice","password":"123456"}`
2. `POST /api/auth/login` — 拿到 `data.token`
3. `POST /api/kbs`（Header: `Authorization: Bearer <token>`）— `{"name":"产品手册"}`
4. `POST /api/documents/upload`（form-data: `file` + `kbId`）— 上传一个 PDF/TXT
5. `GET /api/documents/{docId}` — 等几秒后确认 `status` 为 `INDEXED`（失败为 `FAILED`，看 AI 服务日志）
6. `POST /api/conversations` — `{"kbId":1}`，然后 `POST /api/conversations/{id}/messages` — `{"question":"..."}`
7. 返回 `answer` + `citations`（文档名、片段、页码）

## API 一览

| 模块 | 接口 |
| :--- | :--- |
| 认证 | `POST /api/auth/register`、`POST /api/auth/login` |
| 知识库 | `GET/POST /api/kbs`、`GET/DELETE /api/kbs/{kbId}` |
| 文档 | `POST /api/documents/upload`、`GET /api/documents?kbId=`、`GET/DELETE /api/documents/{docId}`、`GET .../preview`（仅 TXT）、`POST .../reindex` |
| 对话 | `POST/GET /api/conversations`、`POST/GET /api/conversations/{id}/messages` |
| 内部回调 | `PUT /internal/documents/{docId}/status`（需 `X-Internal-Token` 头） |

统一响应格式：`{"code":0,"message":"success","data":...}`；业务错误 code 对应 HTTP 状态码（400/401/403/404/409/502）。

## 配置说明

- `application.yml` 中 `app.jwt.secret` 与 `app.internal-token` 为占位值，生产环境务必更换
- Python 侧 `.env` 的 `INTERNAL_TOKEN` 必须与 Java 侧一致
- 上传限制：20MB，仅 PDF/DOCX/TXT（旧版 .doc 请先转 .docx）

## 已知边界（MVP）

- 单轮问答、单租户；多轮上下文、流式输出为后续迭代（表结构已预留）
- 扫描版 PDF 无文本层会索引失败（状态 FAILED），后续可接 OCR
- 审计日志记录注册/登录/上传/删除/提问，可通过 `audit_logs` 表查看
