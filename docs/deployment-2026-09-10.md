# 艺培通生产部署记录

> 首次生产部署 | 2026-09-10 | 目标服务器 `101.35.239.218`
> 镜像版本 `v1.3.0-20260908` | Git 分支 `dev/iteration-next`

## 1. 部署概要

| 项目 | 值 |
|------|-----|
| 访问地址 | http://101.35.239.218 |
| 管理后台 | http://101.35.239.218/ |
| 移动端 H5 | http://101.35.239.218/mobile/ |
| 健康检查 | http://101.35.239.218/healthz |
| 版本信息 | http://101.35.239.218/version |
| 部署目录 | `/opt/educational-administration` |
| 编排文件 | `docker-compose.production.yml` |
| 镜像标签 | `eduadmin-backend:v1.3.0-20260908` / `eduadmin-web:v1.3.0-20260908` |
| 验收结果 | **38/38 通过**（五角色 32 端点 + 6 项越权拒绝） |

## 2. 服务器信息

| 项目 | 值 |
|------|-----|
| 主机名 | `VM-4-17-ubuntu` |
| 系统 | Ubuntu 26.04 LTS（内核 7.0.0-14-generic） |
| 规格 | 4 核 / 3.6 GB 内存 / 39 GB 磁盘（已用 5.6 GB） |
| SSH | `ssh community-server`（User `ubuntu`，密钥 `~/.ssh/id_ed25519`） |
| 时区 | Asia/Shanghai (CST) |
| Docker | 29.1.3（Ubuntu 官方包，非 Docker CE 源） |
| Compose | 2.40.3 |
| 镜像加速 | `https://mirror.ccs.tencentyun.com`（腾讯云内网） |
| apt 源 | `http://mirrors.tencentyun.com/ubuntu`（腾讯云内网） |

> 注意：本次连接时服务器主机密钥已变更（原记录 3 条密钥失效），已重新采集
> ED25519 指纹 `SHA256:Qdx9UJWOilAz/W+5QYJM4Gfs6+QIVZV2i6WVQhPTAIw` 并更新本地
> `known_hosts`，旧文件备份为 `~/.ssh/known_hosts.bak-20260910-203656`。

## 3. 容器拓扑

```
Internet :80
    │
    ▼
 caddy (caddy:2.8-alpine)          ← 监听 80/443，反向代理到 web:80
    │
    ▼
 web (eduadmin-web:v1.3.0-...)     ← nginx 1.27-alpine
    ├── /            → 管理后台静态资源（admin-web/dist）
    ├── /mobile/     → 移动端 H5（h5-release）
    ├── /api/        → 反代 backend:8080（含 SSE 长连配置）
    ├── /healthz     → 反代 actuator/health/readiness
    └── /version     → 反代 actuator/info
    │
    ▼
 backend (eduadmin-backend:v1.3.0-...)  ← Temurin 17 JRE，-Xmx512m
    │
    ▼
 mysql (mysql:8.0)                  ← 数据卷 mysql-data，初始化 schema.sql + data.sql
```

所有容器 `restart: unless-stopped`，服务器重启后自动拉起。

## 4. 部署步骤回顾

1. **网络修正**：服务器主机密钥变更 → 备份并清理旧记录 → 重新采集指纹
2. **环境准备**：apt 内网源安装 `docker.io` + `docker-compose-v2`（22 秒）
3. **镜像加速**：写入 `/etc/docker/daemon.json`（`mirror.ccs.tencentyun.com` + 日志轮转 10m×3）
4. **基础镜像**：拉取 mysql:8.0 / caddy:2.8-alpine / eclipse-temurin:17-jre-jammy / nginx:1.27-alpine（2 分 26 秒）
5. **制品上传**：`eduadmin.jar`(62MB) + admin-dist + mobile-dist → `/opt/educational-administration/artifacts/`
6. **镜像组装**：使用轻量 Dockerfile（仅 COPY 制品，跳过 maven/npm 重构建）
7. **配置生成**：`.env`（600 权限，密码 `openssl rand -hex` 生成）
8. **启动**：`docker compose up -d` → MySQL healthy → backend/web/caddy 依次启动
9. **验证**：数据库 38 表 / 22 用户 / Flyway V5→V8、外部可达性、38 项验收

### 关键设计验证

- **Flyway baseline 机制正确工作**：`schema.sql` 建库到 V5 状态 → `baseline-on-migrate`
  记为 V5 → 仅执行 V6/V7/V8 增量。三个迁移均为幂等写法
  （`information_schema` 探测 + `PREPARE/EXECUTE`），即使 schema.sql 已包含
  `student_risk_followup` 表也不会冲突。
- **生产配置校验通过**：`ProductionConfigurationValidator` 要求 DB 密码 ≥16 字符、
  JWT ≥32 字节、CORS 为精确 origin，当前配置均满足。

### 对项目文件的一处修改

`deploy/docker-compose.production.yml` 为 backend 增加环境变量
`APP_IMAGE_TAG: ${BACKEND_IMAGE}`，使 `/actuator/info` 能返回真实镜像标签
（原为 `unknown`，不满足 `RELEASE_RUNBOOK.md` 中对发布可追溯性的要求）。
**该修改尚未提交，需要纳入版本库。**

## 5. 验证结果

### 数据库

| 检查项 | 结果 |
|--------|------|
| 表数量 | 38（37 业务表 + `flyway_schema_history`） |
| 用户数 | 22 |
| 学员数 | 12 |
| Flyway 迁移 | V5 baseline → V6 → V7 → V8，全部 `success=1` |

### 外部可达性

| 端点 | 状态 |
|------|------|
| `/healthz` | 200 |
| `/`（管理后台） | 200 |
| `/mobile/`（移动端） | 200 |
| `/api/auth/login` | 200（返回 JWT + permissions） |

### 五角色权限验收（38/38）

| 分组 | 通过 |
|------|------|
| 超级管理员 admin | 12/12 |
| 教务管理员 edu | 6/6 |
| 财务管理员 finance | 3/3 |
| 教师 teacher1 | 4/4 |
| 家长 parent1 | 7/7 |
| 越权拒绝（401/403） | 6/6 |

### 可观测性

- `/actuator/prometheus` 暴露 **265 条**指标
- 业务指标正常：`eduadmin_refunds_pending`、`eduadmin_login_failures_total{reason="invalid"/"locked"}`
- `/version` 返回 `{"app":{"image-tag":"eduadmin-backend:v1.3.0-20260908"}}`

## 6. 运维手册

均在服务器 `/opt/educational-administration` 目录下执行。

```bash
# 查看状态
docker compose -f docker-compose.production.yml ps

# 查看日志
docker logs -f educational-administration-backend-1
docker logs --tail=100 educational-administration-mysql-1

# 重启单个服务
docker compose -f docker-compose.production.yml restart backend

# 停止 / 启动全部
docker compose -f docker-compose.production.yml down
docker compose -f docker-compose.production.yml up -d

# 进入数据库
docker exec -it educational-administration-mysql-1 \
  mysql -uroot -p"$(grep '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2)" -D edu_admin

# 回归验收
bash acceptance_test.sh http://localhost
```

### 敏感文件位置

| 文件 | 说明 |
|------|------|
| `/opt/educational-administration/.env` | 数据库密码、JWT 密钥、CORS 配置（权限 600） |
| `~/.ssh/known_hosts` | 服务器指纹（备份 `known_hosts.bak-20260910-203656`） |

> `.env` 含明文生产凭据，**不得提交到版本库**，也不要在截图中暴露。

## 7. 已知事项与后续建议

| 优先级 | 事项 | 说明 |
|--------|------|------|
| P1 | 备案与域名 | 当前 IP 直连走 HTTP，无 TLS。若需 HTTPS 需域名解析到该 IP 并备案，改 `SITE_ADDRESS` 为域名即可自动签发证书 |
| P1 | 启用发布门禁 | 首次部署未走 `release-production.sh`（该脚本假设已有上一版镜像做对比）。下次升级发布应改用它 |
| P2 | 数据库备份 | `deploy/scripts/backup-production.sh` 与 `run-scheduled-backup.sh` 尚未配置定时任务 |
| P2 | 演示数据时效性 | `data.sql` 为固定日期数据。若答辩时看板显示负营收，原因为「当月缴费为 0、退费留存」，属口径正常而非缺陷 |
| P2 | 镜像不可变性 | 当前镜像 tag 为日期版本；建议后续改用 Git commit SHA 作为 tag |
| P3 | 安全加固 | 建议关闭 3306 对外暴露（当前仅容器内网，安全组请勿开放）；为 ubuntu 用户禁用密码登录 |
