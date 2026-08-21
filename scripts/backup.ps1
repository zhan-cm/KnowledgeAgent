# 备份 PostgreSQL 数据库到 backups/ 目录（需 Docker 容器 knowledge-postgres 运行中）
$ErrorActionPreference = "Stop"
$date = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $PSScriptRoot "..\backups"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$out = Join-Path $outDir "knowledge_agent_$date.sql"

docker exec knowledge-postgres pg_dump -U admin knowledge_agent > $out
Write-Host "备份完成: $out"
Write-Host "恢复命令: docker exec -i knowledge-postgres psql -U admin -d knowledge_agent < $out"
