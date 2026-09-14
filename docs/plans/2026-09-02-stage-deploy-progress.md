# Stage deploy progress — 2026-09-02

Record of pushing `main` to `stage` and deploying to pi-node, including issues hit and how they were resolved.

## What was done

1. **Enabled auto-restart on Pi reboot** — added `restart: always` to all three
   stage services (`postgres`, `api`, `dashboard`) in
   `infra/docker-compose.infra-stage.yml` so the stage stack survives a Pi
   power cycle without manual intervention.

2. **Synced `stage` to `main`** — `origin/stage` was 14 commits behind
   `origin/main`. Fast-forwarded `stage` to `main`'s tip (`git push origin
   main:stage`).

3. **Recovered in-progress uncommitted work** — the `stage` git worktree
   (`.worktrees/stage`) had uncommitted changes blocking the fast-forward
   merge. Stashed (not discarded) before merging, then popped and resolved
   conflicts:
   - `dashboard/src/components/Sidebar.vue` — restored the missing "News"
     nav item (route already existed, just wasn't linked).
   - `dashboard/src/api/types.ts`, `CandidateExplorerView.vue/.test.ts` — the
     stash's changes here were pure Prettier reformatting with no functional
     delta versus `main`'s already-shipped rewrite (pagination, run
     pause/resume); `main`'s version was kept as-is.
   - Landed as commit `25fca64b` on `main`, then fast-forwarded into `stage`.

4. **Fixed a Flyway migration numbering conflict** — the stash had renamed
   `V35__add_candidate_scan_tables.sql` → `V37`, freeing up `V35`/`V36` for
   two new migrations (`sentiment_evaluation_audit`,
   `backfill_stocks_from_watchlist`). `main` had independently added its own
   `V36__widen_symbol_column.sql`, causing a version collision.
   - Initially resolved by keeping the *original* `V35` untouched (since
     renaming an already-applied Flyway migration risks a checksum
     validation failure) and renumbering only the two new files to
     `V37`/`V38`.
   - **This was wrong for the live stage database.** Querying
     `flyway_schema_history` directly on pi-node's Postgres showed the
     *renamed* scheme (`V35`=sentiment audit, `V36`=backfill,
     `V37`=candidate scan tables) had already been applied by an earlier,
     separate deploy that predated this session. Corrected the file
     numbering to match what was actually already applied, and slotted
     `widen_symbol_column` in as `V38`. Landed as commit `caa71792`.

5. **Fixed a pre-existing Prettier failure on `main`** — `dashboard/src/
   utils/format.ts` and `dashboard/src/views/SettingsView.vue` failed
   `format:check`, blocking every stage build regardless of the changes
   above. Fixed with the project's local Prettier and committed to `main`
   (`1e29b8e1`).

6. **Deployed to pi-node** — `./dev-stack.sh stage`. Two attempts failed
   mid-transfer with `Connection to piworm.local closed by remote host`
   during the ~167MB backend artifact `scp` (transient Wi-Fi issue, not a Pi
   resource problem — checked `uptime`/`free -h`/`dmesg` on pi-node, all
   healthy). A subsequent retry completed cleanly.

## Final verified state

- `main` and `stage` both at `caa71792`.
- API (`http://piworm.local:8081/actuator/health`): `UP`, DB connected.
- Dashboard (`http://piworm.local:8082`): `200 OK`.
- `flyway_schema_history` on stage Postgres, most recent rows:

  | version | description |
  |---|---|
  | 38 | widen symbol column |
  | 37 | add candidate scan tables |
  | 36 | backfill stocks from watchlist |
  | 35 | sentiment evaluation audit |
  | 34 | add llm analysis result |

## Lessons / things to watch next time

- **Don't assume a migration file's applied history from local git state
  alone.** The `.worktrees/stage` checkout can drift from what has actually
  been run against the live stage database if a prior deploy happened
  outside the normal `main` → `stage` flow (e.g. directly from the
  worktree). Always check `flyway_schema_history` on the target database
  before renumbering migrations that touch already-shipped version numbers.
- **A shared worktree can be touched by other concurrent sessions.** Mid-task,
  `HEAD` in `.worktrees/stage` was observed to revert from a commit that had
  just been fast-forwarded onto back to its prior commit, with the old dirty
  files reappearing — most likely another session operating on the same
  worktree path. Re-verify `git rev-parse HEAD` immediately before a
  build/deploy step rather than trusting the outcome of an earlier command
  in the same turn.
- **`scp`/`ssh` to piworm.local can drop mid-transfer** for large artifacts
  even when the Pi itself is healthy; a plain retry has been sufficient so
  far.
