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
- 检索流程：向量召回 top20 → `bge-reranker-base` 交叉编码器重排 → 取 top5 给 LLM（可配置关闭）
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

### 4. 使用

- **前端界面**：浏览器打开 http://localhost:8080（注册/登录 → 选知识库 → 上传文档 → 流式对话）
- **API 文档**：http://localhost:8080/swagger-ui.html（Swagger UI，JWT 认证可直接调试）
- **Postman 验证链路**：

1. `POST /api/auth/register` — `{"username":"alice","password":"123456"}`
2. `POST /api/auth/login` — 拿到 `data.token`
3. `POST /api/kbs`（Header: `Authorization: Bearer <token>`）— `{"name":"产品手册"}`
4. `POST /api/documents/upload`（form-data: `file` + `kbId`）— 上传一个 PDF/TXT
5. `GET /api/documents/{docId}` — 等几秒后确认 `status` 为 `INDEXED`（失败为 `FAILED`，看 AI 服务日志）
6. `POST /api/conversations` — `{"kbId":1}`，然后 `POST /api/conversations/{id}/messages` — `{"question":"..."}`
7. 返回 `answer` + `citations`（文档名、片段、页码）

## 容器化部署（全栈）

一键把 PostgreSQL + Redis + Java 后端 + Python AI 服务全部容器化（用于部署到服务器）：

```bash
# 需先设置环境变量：DEEPSEEK_API_KEY（必填）、JWT_SECRET、INTERNAL_TOKEN
docker compose -f docker-compose.prod.yml up -d --build
```

- 前端 + API 文档：http://服务器:8080
- `ai-service/models/` 以只读卷挂载进 AI 容器（模型不打进镜像，构建更快）
- `data/documents/` 通过命名卷 `documents` 同时挂载到后端与 AI 容器同一路径 `/app/data/documents`（因为 Redis Stream 传递的是绝对路径，两容器必须一致）
- 后端多阶段构建（Maven 打包 → JRE 运行），配置全部走环境变量
- 生产环境务必通过环境变量注入 `JWT_SECRET`、`INTERNAL_TOKEN`、`DEEPSEEK_API_KEY`，不要用默认占位值

> 注意：首次构建 AI 镜像需下载 torch（约 2GB），耗时较长；国内可给 Docker 配置镜像加速器。

## 可观测性（Prometheus）

- 后端指标：http://localhost:8080/actuator/prometheus（问答次数 `ai_query_count`、问答耗时 `ai_query_duration_seconds`）
- AI 服务指标：http://localhost:8000/metrics（检索/生成耗时 `ai_query_duration_seconds{phase=...}`、索引耗时 `ai_index_duration_seconds`）
- 用 Prometheus 抓取（已提供 `monitoring/prometheus.yml`）：

```bash
docker run -d --name prometheus -p 9090:9090 \
  -v "$(pwd)/monitoring/prometheus.yml:/etc/prometheus/prometheus.yml" \
  prom/prometheus
```

然后浏览器打开 http://localhost:9090，即可查询/画图（后续可接 Grafana）。

## OAuth2 登录（可选）

默认关闭。开启后员工可用第三方账号（示例为 GitHub）登录，自动映射到本地用户并签发 JWT：

1. 到 https://github.com/settings/developers 新建 OAuth App，回调地址填 `http://localhost:8080/login/oauth2/code/github`
2. 设置环境变量后启动：

```bash
OAUTH2_ENABLED=true OAUTH2_CLIENT_ID=xxx OAUTH2_CLIENT_SECRET=xxx mvnw.cmd spring-boot:run
```

- 前端登录页会出现"GitHub 登录"入口，走标准 OAuth2 授权码流程
- 登录用户名为 `github_{login}`，自动建档（角色 USER）
- 企业生产可把 `OAuth2Config` 里的 GitHub 换成企业微信/钉钉/通用 OIDC（需要对应平台的应用注册与回调）

## 测试与 CI

- Java：`mvnw.cmd test`（JwtUtil / AuthService / ConversationService 单元测试）
- Python：`cd ai-service && pip install -r requirements-dev.txt && python -m pytest tests/ -v`
- CI：GitHub Actions（每次 push 自动跑 Java 构建测试 + Python pytest）

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

- 全部敏感配置支持环境变量注入（默认值为本地开发占位）：`DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`、`REDIS_HOST/REDIS_PORT`、`JWT_SECRET/JWT_EXPIRATION_MS`、`INTERNAL_TOKEN`、`AI_BASE_URL`
- 生产环境务必通过环境变量设置 `JWT_SECRET` 和 `INTERNAL_TOKEN`，不要使用默认占位值
- Python 侧 `.env` 的 `INTERNAL_TOKEN` 必须与 Java 侧一致
- 上传限制：20MB，仅 PDF/DOCX/TXT（旧版 .doc 请先转 .docx）
- 接口限流：认证接口 10 次/分钟/IP，问答接口 20 次/分钟/IP，其他业务接口 300 次/分钟/IP

## 已知边界（MVP）

- 单轮问答、单租户；多轮上下文、流式输出为后续迭代（表结构已预留）
- 扫描版 PDF 无文本层会索引失败（状态 FAILED），后续可接 OCR
- 审计日志记录注册/登录/上传/删除/提问，可通过 `audit_logs` 表查看
