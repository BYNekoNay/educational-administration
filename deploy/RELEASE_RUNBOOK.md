# Production release and rollback

`scripts/release-production.sh` is the authoritative release gate. It records
the previous images and schema version, rejects known high-value data
violations, starts the selected immutable images, and proves the following
before reporting success:

- MySQL, backend, and web container health;
- exact backend and web image tag;
- expected Flyway version and successful application readiness;
- build information containing the image tag;
- internal Prometheus output containing every required business metric;
- admin `/`, H5 `/mobile/`, and the five-role API smoke matrix.

The script stores `compose ps`, bounded logs, image state, probe responses, and
the failing step under a mode-700 `release-evidence` directory. Logs can still
contain operational data; restrict access and apply a retention policy.

## Failure decision

Do not equate a running container with a successful release, and do not run an
automatic rollback after a failed migration.

1. Inspect the evidence directory and `flyway_schema_history`.
2. If the database version is unchanged, use an explicit image rollback.
3. If the database version changed, keep the new database state and prepare a
   tested forward migration or application fix. Do not run `flyway clean`, edit
   migration checksums, or restore an older database dump merely to make the
   application start.
4. Use the backup restore runbook only for a deliberate disaster-recovery
   decision with an accepted RPO, not as a routine deployment rollback.

## Explicit image rollback

Select the exact evidence directory created by the failed release. The rollback
script refuses to continue unless the current Flyway version equals the version
recorded before the release:

```bash
export ROLLBACK_TARGET=production
bash scripts/rollback-images.sh \
  /opt/educational-administration/release-evidence/YYYYMMDDTHHMMSSZ-SHA
```

After an exact interactive confirmation, it starts the recorded backend and web
images, waits for health, and atomically updates only the two image entries in
the deployment `.env`. Database and uploads are not changed.

## Required release inputs

- `PUBLIC_BASE_URL`: externally reachable origin, without a trailing path.
- `EXPECTED_IMAGE_TAG`: immutable Git commit SHA or approved release tag.
- `EXPECTED_SCHEMA_VERSION`: current repository migration version (`7`).
- `SMOKE_PASSWORD_FILE` and optional role-specific smoke usernames consumed by
  `acceptance_test.sh`; the workflow creates a mode-600 file from the protected
  GitHub Environment secret.

The deployment workflow uploads the Compose/Caddy manifests, release and backup
scripts, the invariant SQL, and the acceptance script before invoking the gate.
