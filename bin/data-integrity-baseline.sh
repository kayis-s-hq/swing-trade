#!/usr/bin/env bash
set -euo pipefail

# Read-only Phase 0 inventory. No DELETE/UPDATE/INSERT statements are present.
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
FROM_DATE="${FROM_DATE:?set FROM_DATE=YYYY-MM-DD}"
TO_DATE="${TO_DATE:?set TO_DATE=YYYY-MM-DD}"
OUTPUT_FILE="${OUTPUT_FILE:-data-integrity-baseline-${FROM_DATE}-${TO_DATE}.json}"

curl --fail --silent --show-error \
  "$API_BASE_URL/api/ingestion/reconcile?from=$FROM_DATE&to=$TO_DATE" > "$OUTPUT_FILE"

echo "Candle baseline written to $OUTPUT_FILE"

if [[ "${RUN_POSITION_INVENTORY:-false}" == "true" ]]; then
  : "${DATABASE_URL:?set DATABASE_URL for the read-only position inventory}"
  psql "$DATABASE_URL" --csv <<'SQL'
SELECT p.id AS position_pk,
       p.symbol,
       p.broker_type,
       p.status,
       p.position_id,
       (SELECT count(*) FROM trades t WHERE t.position_id = p.id) AS linked_trades,
       (SELECT count(*) FROM paper_trading_orders o WHERE o.symbol = p.symbol) AS symbol_orders,
       CASE
         WHEN p.position_id IS NULL THEN 'null position_id'
         WHEN p.position_id LIKE 'LEGACY_%' THEN 'reserved legacy identifier'
         WHEN p.broker_type = 'PAPER' THEN 'paper candidate requires operator review'
         ELSE 'non-paper position; retain pending review'
       END AS classification_reason
FROM positions p
ORDER BY p.id;
SQL
fi
