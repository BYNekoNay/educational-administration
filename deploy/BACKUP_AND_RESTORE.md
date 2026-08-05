# Production backup and recovery runbook

These tools create one encrypted artifact containing a MySQL dump, the
`uploads-data` volume contents, a manifest, and SHA-256 checksums. Run them on
the ECS host from the deployment directory. They never print encryption or
database passwords.

## Targets

- RPO: at most 24 hours with a daily successful backup copied off the ECS host.
- RTO: restore service within 60 minutes after the restore decision.
- Retention: 14 daily local artifacts by default; keep a separate off-host copy
  in OSS or another access-controlled location through `BACKUP_OFFSITE_HOOK`.
- Drill: run an isolated restore drill at least monthly and after backup-tool or
  major MySQL changes.

The backup briefly stops `backend` so the database and uploads snapshot describe
one point in time. Schedule it during the lowest-traffic window.

## Prerequisites and environment

The ECS host needs Bash, Docker with Compose v2, OpenSSL, GNU tar/coreutils, and
access to the configured helper/MySQL images. Scripts default to
`docker-compose.yml` beside this runbook on the server, then fall back to the
repository name `docker-compose.production.yml`. They pass the deployment
`.env` to Docker Compose as a dotenv file; they never execute it as shell code.
Override the manifest or dotenv path with an absolute `COMPOSE_FILE` or
`COMPOSE_ENV_FILE` when necessary.

The backup and restore scripts read the database name from the running MySQL
container and use its root password only inside that container. The host shell
does not need to load either value. Provide a separate backup key of at least
32 high-entropy characters:

```bash
cd /opt/educational-administration
read -rsp 'Backup encryption key: ' BACKUP_ENCRYPTION_KEY && echo
export BACKUP_ENCRYPTION_KEY
export BACKUP_DIR=/var/backups/educational-administration
export BACKUP_RETENTION_DAYS=14
export COMPOSE_FILE=/opt/educational-administration/docker-compose.yml
```

Do not store `BACKUP_ENCRYPTION_KEY` in the repository, the Compose `.env`, shell
history, or the same storage as the artifacts. Loss of this key makes every
artifact unrecoverable.

For off-site copies, set `BACKUP_OFFSITE_HOOK` to an absolute, non-symlinked,
executable file. After the backend is restarted, the backup script invokes it
with the encrypted artifact path as its only argument. The hook can upload to
OSS, immutable object storage, or a mounted remote repository and must exit
nonzero on failure. The script removes `BACKUP_ENCRYPTION_KEY` and
known application/database credentials from the hook environment. A hook
failure keeps the local artifact, prints its exact path, and makes the backup
job fail so monitoring can alert.

## Create and inspect a backup

```bash
bash scripts/backup-production.sh
```

The command prints the exact absolute artifact path only after encryption and
the backend restart succeed. Retention cleanup is limited to immediate regular
files matching `edu-admin-*.tar.enc` inside the resolved, explicitly supplied
`BACKUP_DIR`.

Before treating a backup as successful, copy the artifact off-host and run:

```bash
bash scripts/restore-drill.sh \
  /var/backups/educational-administration/edu-admin-YYYYMMDDTHHMMSSZ.tar.enc
```

The drill decrypts and verifies all checksums, extracts uploads in a temporary
directory, imports SQL into a disposable `--network none` MySQL container, and
runs business assertions for usable login records, valid lesson balances, valid
refund links, and readable attachments. When the uploads archive is empty, the
default equivalent assertion verifies that the restored database has no
attachment references. It does not query Compose or mount production volumes.

Each database assertion must return exactly `1`. Deployments with customized
schemas may provide equivalent read-only SQL through
`RESTORE_DRILL_LOGIN_SQL`, `RESTORE_DRILL_LESSON_BALANCE_SQL`,
`RESTORE_DRILL_REFUND_SQL`, and `RESTORE_DRILL_ATTACHMENT_SQL`. Treat these as
trusted operator configuration; they execute only inside disposable MySQL.

## Restore production

First stop external release activity and identify one exact artifact. Do not use
a glob or `latest` symlink. Ensure the currently configured application image is
compatible with the backup's schema, then use an interactive TTY:

```bash
export RESTORE_TARGET=production
bash scripts/restore-production.sh \
  /var/backups/educational-administration/edu-admin-YYYYMMDDTHHMMSSZ.tar.enc
```

Production restore is denied unless `RESTORE_TARGET` is exactly `production`.
This one-command opt-in is separate from the exact interactive confirmation
phrase requested after every checksum and preflight check.

The script decrypts the artifact, checks its exact members and SHA-256 manifest,
validates both archives, confirms the database name, checks MySQL and the
uploads volume, and only then asks for an exact confirmation phrase. After
confirmation it stops `backend`, imports the dump, replaces uploads, and starts
`backend` again.

If the destructive stage fails, the backend remains stopped. Investigate before
manually starting it; do not serve a partially restored state. The dump restores
the schema and data captured at backup time, but application-image rollback and
schema changes made outside that dump are not automatic. Coordinate any image
rollback or forward migration separately.

After restoration, verify container state, API health, admin login, H5 teacher
login, recent enrollments/payments, and a representative uploaded file before
reopening traffic.

## GitHub Actions schedule

`.github/workflows/backup.yml` runs daily at 03:15 Asia/Shanghai. It transfers
the backup key in a run-scoped mode-600 file, creates the encrypted artifact on
ECS, deletes the remote key file, and stores a 30-day off-host copy as a private
GitHub Actions artifact. On the first day of each month it also runs the
isolated restore drill. Configure `BACKUP_ENCRYPTION_KEY` only as a protected
`production` Environment secret.

## Optional host cron

Place the required variables in a root-owned file such as
`/etc/educational-administration-backup.env` and set mode `600`. It should define
`BACKUP_ENCRYPTION_KEY`, `BACKUP_DIR`, `BACKUP_RETENTION_DAYS`, `COMPOSE_FILE`,
and optionally `COMPOSE_ENV_FILE` and `BACKUP_OFFSITE_HOOK`. Database passwords
must remain only in the Compose dotenv file.
Then add a root crontab entry:

```cron
15 03 * * * /bin/bash -c 'set -a; . /etc/educational-administration-backup.env; set +a; /opt/educational-administration/scripts/backup-production.sh' >> /var/log/educational-administration-backup.log 2>&1
```

Alert on a missing daily artifact or any nonzero exit. Cron success alone is not
proof of recoverability; the monthly drill is the recovery control.

## Verification policy

These are host-level operational scripts whose meaningful integration test
would stop the production backend and read or replace production data. Unit
tests are deliberately omitted. Replacement verification is `bash -n` for every
script, ShellCheck when available, code review of path/destructive-operation
guards, and a scheduled isolated restore drill against real encrypted artifacts.
