# Stage validation runbook

The manual `./dev-stack.sh stage` deployment path performs read-only validation
against the deployed stage API. Use `STAGE_API_URL` (default
`http://piworm.local:8081`), `STAGE_DASHBOARD_URL`, and `PROMETHEUS_URL`
(`http://piworm.local:9090`) when the defaults do not apply. The stage database
must contain the reviewed NSE holiday calendar and the successful Flyway
version expected by the deployed application.

The former self-hosted GitHub Actions deployment definitions are archived under
`docs/infra/legacy/`; their queued or skipped checks are not authoritative.

The smoke gate checks health, Flyway state, the 14-symbol active universe,
bounded reconciliation coverage, read-only signals and positions endpoints, and
the Prometheus target. It never calls signal generation, reconciliation apply,
backfill, order placement, or other mutating endpoints.

Before any future mutation smoke test, an operator must separately approve the
symbol/range, set the stage-only feature gate and token, capture the dry-run
report, and record the apply response. Restore the feature gate immediately
afterward and retain the request/response and logs with the work record.
