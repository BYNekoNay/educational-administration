# GitHub CI/CD 与部署指南

## 1. 已包含的自动化

- `CI`：在 `master`、`codex/**` 分支推送和面向 `master` 的拉取请求时，执行后端 Maven 测试、管理端测试和构建、移动端 H5 构建及两套容器镜像构建。
- `Deploy`：从 GitHub Actions 手动选择 `staging` 或 `production` 后，构建镜像、发布到 GitHub Container Registry（GHCR），再通过 SSH 更新 Linux 服务器上的 Docker Compose 服务。管理后台发布在 `/`，移动 H5 发布在 `/mobile/`。
- Dependabot：每周检查 GitHub Actions、Maven 和两个 npm 工程的依赖更新。

部署工作流刻意不在推送时自动发布。生产发布须在 GitHub Environments 中通过所选环境的保护规则后才会继续。

## 2. 首次服务器准备

服务器需要 Linux、Docker Engine（含 Compose 插件）和一个可 SSH 登录的普通部署用户。开放 TCP `80` 和 `443`，并让域名 A/AAAA 记录指向该服务器。Caddy 容器负责 HTTPS 证书申请与续期，因此首轮部署前 DNS 必须生效。

为部署用户创建专用 SSH 密钥，将公钥写入服务器的 `~/.ssh/authorized_keys`。用以下命令在可信设备上获取服务器主机指纹，输出内容将作为 GitHub Secret：

```bash
ssh-keyscan -H your-server.example
```

GHCR 镜像默认可能为私有。创建一个仅含 `read:packages` 权限的 GitHub fine-grained token，供服务器拉取镜像使用；不要复用个人全权限令牌。

## 3. GitHub Environments 与 Secrets

在仓库 Settings -> Environments 中创建 `staging` 和 `production`。建议为 `production` 启用 Required reviewers。为每个环境分别设置下列 Secrets：

| Secret | 用途 |
|---|---|
| `DEPLOY_HOST` | 服务器 IP 或主机名 |
| `DEPLOY_USER` | 服务器部署用户名 |
| `DEPLOY_PATH` | 服务器部署目录，例如 `/opt/eduadmin` |
| `DEPLOY_SSH_PRIVATE_KEY` | 部署私钥全文 |
| `DEPLOY_KNOWN_HOSTS` | `ssh-keyscan -H` 输出 |
| `DEPLOY_DOMAIN` | 指向该服务器的域名，不含协议 |
| `MYSQL_PASSWORD` | 应用数据库用户密码 |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码，必须与应用密码不同 |
| `JWT_SECRET` | 至少 32 个随机字符的 JWT 签名密钥 |
| `GHCR_USERNAME` | GitHub 用户名或组织名 |
| `GHCR_TOKEN` | 仅有 `read:packages` 权限的令牌 |

生成密码和 JWT 密钥的示例：

```bash
openssl rand -base64 36
```

## 4. 首次部署与回滚

1. 合并要发布的提交到 `master`，或在目标提交的 Actions 页面运行 `Deploy`。
2. 点击 Run workflow，选择 `staging` 或 `production`。不填写 `image_tag` 时，工作流使用该次运行所选提交的 SHA。
3. 首次运行会上传 Compose、Caddy 和数据库初始化脚本，创建 MySQL 数据卷，并发布管理端、移动 H5、后端和 HTTPS 入口。
4. 访问 `https://<DEPLOY_DOMAIN>` 验证管理端，访问 `https://<DEPLOY_DOMAIN>/mobile/` 验证移动 H5。

回滚时，在包含已知可用提交的 Actions 页面重新运行 `Deploy`，并将 `image_tag` 填为该提交 SHA。MySQL 与上传文件使用 Docker named volume 持久化；首次初始化后不会因重新部署而覆盖数据。

## 5. 运维说明

在服务器上使用下列命令排查：

```bash
cd /opt/eduadmin
docker compose ps
docker compose logs --tail=200 backend
docker compose logs --tail=200 caddy
```

数据库初始化仅发生在 `mysql-data` 卷不存在时。不要通过删除该卷来“重置”生产环境；先备份并按迁移方案操作。应用与数据库仅在 Docker 内部网络通信，公网仅暴露 Caddy 的 `80` 与 `443` 端口。
