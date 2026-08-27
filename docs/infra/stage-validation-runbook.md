# Stage validation runbook

The deployment workflow performs read-only validation against the deployed stage
API. Configure `STAGE_API_URL`, `STAGE_DASHBOARD_URL`, and `PROMETHEUS_URL` as
repository/environment variables when the defaults do not apply. The stage
database must contain the reviewed NSE holiday calendar and the successful
Flyway version expected by the workflow.

The smoke gate checks health, Flyway state, the 14-symbol active universe,
bounded reconciliation coverage, read-only signals and positions endpoints, and
the Prometheus target. It never calls signal generation, reconciliation apply,
backfill, order placement, or other mutating endpoints.

Before any future mutation smoke test, an operator must separately approve the
symbol/range, set the stage-only feature gate and token, capture the dry-run
report, and record the apply response. Restore the feature gate immediately
afterward and retain the request/response and logs with the work record.
