# Production observability

The backend exposes Actuator health, build information, and Prometheus metrics
inside the Compose network. Nginx publishes only `/healthz` and `/version`; keep
`/actuator/prometheus` restricted to the host or an authenticated collector.

Every request receives `X-Request-Id`. A safe caller-provided value is reused;
otherwise the backend generates one. Production logs are JSON and include the
same `requestId`, HTTP method, path without query parameters, status, and
duration. Do not add request bodies, tokens, names, phone numbers, student IDs,
or financial amounts to log fields or metric tags.

## Initial alerts

Start with these thresholds, then tune them against at least two weeks of real
traffic. Every alert must link to the release evidence and request-ID search.

| Signal | Initial condition | Action |
|---|---|---|
| `rate(eduadmin_login_failures_total[5m])` | above 0.2/s for 10m | Check one source/IP concentration and account lockouts; do not log usernames |
| `increase(eduadmin_enrollment_failures_total{reason="server"}[10m])` | greater than 0 | Inspect the current release, DB health, and correlated requests |
| `increase(eduadmin_payment_failures_total{reason="server"}[10m])` | greater than 0 | Stop finance changes and inspect transaction/DB evidence |
| `eduadmin_refunds_pending` | above 10 for 30m | Assign finance review ownership and inspect oldest pending records |
| `increase(eduadmin_lesson_account_cas_failures_total[10m])` | above 5 | Check duplicate/retry traffic and DB contention |
| `increase(eduadmin_schedule_conflicts_total[15m])` | sudden 3x baseline | Check timetable imports and repeated operator actions |
| `increase(eduadmin_notification_failures_total[10m])` | above 20 | Check SSE connectivity, proxy timeouts, and backend resource pressure |
| readiness health | failing for 2 consecutive probes | Block release or remove the instance from traffic |

The first response to a failed release is evidence collection. Image rollback
is allowed only when the Flyway version did not change; otherwise use a forward
database/application repair as documented in `RELEASE_RUNBOOK.md`.

Both the GitHub container release gate and the ECS release gate fetch the
internal Prometheus endpoint and require every metric in the table above to be
registered. The response is retained with the other release evidence; the
endpoint is not exposed through the public web container.
