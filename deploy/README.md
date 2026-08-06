# Production Deployment Configuration

The production Compose manifest activates the `prod` Spring profile. The backend
refuses to start unless the following values are present and meet the safety
requirements below. Configure them as GitHub Environment secrets; do not commit
them to a `.env` file.

| GitHub Environment secret | Requirement | Runtime variable |
| --- | --- | --- |
| `MYSQL_PASSWORD` | Strong application database password; at least 16 UTF-8 bytes and not a development default. | `DB_PASSWORD` |
| `MYSQL_ROOT_PASSWORD` | Strong MySQL administrative password, distinct from `MYSQL_PASSWORD`. | `MYSQL_ROOT_PASSWORD` |
| `JWT_SECRET` | Random secret of at least 32 UTF-8 bytes; do not reuse development values. | `JWT_SECRET` |
| `DEPLOY_ORIGIN` | One or more exact comma-separated HTTP(S) origins, such as `https://admin.example.com,https://app.example.com`. Wildcards are rejected. | `APP_CORS_ALLOWED_ORIGINS` |
| `DEPLOY_SITE_ADDRESS` | Caddy listener address, such as `:80` for a direct IP deployment or a domain name for HTTPS. | `SITE_ADDRESS` |

Generate a JWT secret with a cryptographically secure generator. For example,
on a trusted administrator workstation: `openssl rand -base64 48`. Store the
result only in the GitHub Environment secret.

The GitHub deployment workflow writes these values to the ECS deployment
directory at runtime. That generated `.env` file remains ignored and must not
be copied back into the repository.
