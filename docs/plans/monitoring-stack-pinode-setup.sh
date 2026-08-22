#!/bin/bash
# =============================================================================
# SwingTrade Monitoring Stack — pi-node Setup Script
# =============================================================================
# Run this on pi-node (piworm.local) to complete the monitoring setup.
# Prerequisites: Prometheus and Grafana are already running on pi-node.
# =============================================================================

set -euo pipefail

echo "=== SwingTrade Monitoring Setup ==="
echo ""

# =============================================================================
# 1. Prometheus Config — Remove swing-trade-dev, keep swing-trade-stage
# =============================================================================
echo "[1/5] Updating Prometheus config..."

cat > /home/dietpi/pi-stack/prometheus/prometheus.yml << 'EOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: prometheus
    static_configs:
      - targets: ["localhost:9090"]

  - job_name: node
    static_configs:
      - targets: ["node_exporter:9100"]
        labels:
          instance: pi

  - job_name: grafana
    static_configs:
      - targets: ["grafana:3000"]

  - job_name: caddy
    static_configs:
      - targets: ["caddy:9187"]

  - job_name: swing-trade-stage
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["swing-trade-stage-api:8080"]
        labels:
          application: swing-trade-stage
          module: api

  - job_name: vllm
    metrics_path: /metrics
    scheme: https
    scrape_interval: 5m
    static_configs:
      - targets: ["u425-b06e-bde186b5.singapore-b.gpuhub.com"]
        labels:
          instance: gpuhub-vllm
    tls_config:
EOF

echo "  Config written to /home/dietpi/pi-stack/prometheus/prometheus.yml"

# Reload Prometheus (uses --web.enable-lifecycle)
echo "  Reloading Prometheus..."
docker exec pi-prometheus curl -s -X POST http://localhost:9090/-/reload

# Verify targets
sleep 3
echo ""
echo "  Prometheus targets:"
docker exec pi-prometheus curl -s http://localhost:9090/api/v1/targets \
  | python3 -c "
import sys, json
data = json.load(sys.stdin)
for t in data['data']['targets']:
    job = t['labels'].get('job', '?')
    health = t['health']
    status = 'UP' if health == 'up' else 'DOWN'
    print(f'    {job:25s} {status}')
"

echo ""

# =============================================================================
# 2. Grafana Datasource — Configure Prometheus as datasource
# =============================================================================
echo "[2/5] Setting up Grafana Prometheus datasource..."

mkdir -p /home/dietpi/pi-stack/grafana/provisioning/datasources

cat > /home/dietpi/pi-stack/grafana/provisioning/datasources/prometheus.yml << 'EOF'
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
    jsonData:
      timeInterval: 15s
      httpMethod: POST
EOF

echo "  Datasource config written to /home/dietpi/pi-stack/grafana/provisioning/datasources/prometheus.yml"
echo ""

# =============================================================================
# 3. Grafana Dashboard Provider — Auto-load dashboards
# =============================================================================
echo "[3/5] Setting up Grafana dashboard provider..."

mkdir -p /home/dietpi/pi-stack/grafana/provisioning/dashboards

cat > /home/dietpi/pi-stack/grafana/provisioning/dashboards/dashboards.yml << 'EOF'
apiVersion: 1

providers:
  - name: default
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards
EOF

echo "  Dashboard provider config written to /home/dietpi/pi-stack/grafana/provisioning/dashboards/dashboards.yml"
echo ""

# =============================================================================
# 4. SwingTrade Dashboard — Custom Grafana dashboard
# =============================================================================
echo "[4/5] Writing SwingTrade dashboard..."

cat > /home/dietpi/pi-stack/grafana/provisioning/dashboards/swingtrade.json << 'DASHEOF'
{
  "annotations": { "list": [] },
  "description": "SwingTrade monitoring dashboard — trades, signals, portfolio, jobs, LLM, data ingestion",
  "editable": true,
  "fiscalYearStartMonth": 0,
  "graphTooltip": 0,
  "id": null,
  "links": [],
  "panels": [
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "thresholds" }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 4, "w": 4, "x": 0, "y": 0 },
      "id": 1,
      "options": { "colorMode": "background", "graphMode": "area", "justifyMode": "auto", "orientation": "auto", "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false }, "textMode": "auto" },
      "targets": [ { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "portfolio_active_positions", "refId": "A" } ],
      "title": "Active Positions",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "thresholds" }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "red", "value": null }, { "color": "yellow", "value": 0 }, { "color": "green", "value": 1 } ] }, "unit": "bool" }, "overrides": [] },
      "gridPos": { "h": 4, "w": 4, "x": 4, "y": 0 },
      "id": 2,
      "options": { "colorMode": "background", "graphMode": "area", "justifyMode": "auto", "orientation": "auto", "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false }, "textMode": "auto" },
      "targets": [ { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "killswitch_active", "refId": "A" } ],
      "title": "Kill Switch",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "thresholds" }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 4, "w": 4, "x": 8, "y": 0 },
      "id": 3,
      "options": { "colorMode": "background", "graphMode": "area", "justifyMode": "auto", "orientation": "auto", "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false }, "textMode": "auto" },
      "targets": [ { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "job_runs_active", "refId": "A" } ],
      "title": "Active Job Runs",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "thresholds" }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 4, "w": 4, "x": 12, "y": 0 },
      "id": 4,
      "options": { "colorMode": "background", "graphMode": "area", "justifyMode": "auto", "orientation": "auto", "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false }, "textMode": "auto" },
      "targets": [ { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "trades_open_total", "refId": "A" } ],
      "title": "Total Trades Opened",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "thresholds" }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 4, "w": 4, "x": 16, "y": 0 },
      "id": 5,
      "options": { "colorMode": "background", "graphMode": "area", "justifyMode": "auto", "orientation": "auto", "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false }, "textMode": "auto" },
      "targets": [ { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "signals_generated_total", "refId": "A" } ],
      "title": "Total Signals Generated",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "thresholds" }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 4, "w": 4, "x": 20, "y": 0 },
      "id": 6,
      "options": { "colorMode": "background", "graphMode": "area", "justifyMode": "auto", "orientation": "auto", "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false }, "textMode": "auto" },
      "targets": [ { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "data_candles_ingested_total", "refId": "A" } ],
      "title": "Candles Ingested",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "palette-classic" }, "custom": { "axisBorderShow": false, "axisCenteredZero": false, "axisLabel": "", "axisWidth": 10, "fillOpacity": 80, "gradientMode": "none", "hideFrom": { "legend": false, "tooltip": false, "viz": false }, "lineWidth": 2, "scaleDistribution": { "type": "linear" }, "thresholdsStyle": { "mode": "off" } }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 4 },
      "id": 7,
      "options": { "legend": { "calcs": ["lastNotNull", "max"], "displayMode": "table", "placement": "bottom" }, "tooltip": { "mode": "single" } },
      "targets": [
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "rate(trades_open_total[5m])", "legendFormat": "trades opened", "refId": "A" },
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "rate(trades_closed_total[5m])", "legendFormat": "trades closed", "refId": "B" },
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "rate(signals_generated_total[5m])", "legendFormat": "signals generated", "refId": "C" }
      ],
      "title": "Trade & Signal Rate (5m)",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "palette-classic" }, "custom": { "axisBorderShow": false, "axisCenteredZero": false, "axisLabel": "", "axisWidth": 10, "fillOpacity": 80, "gradientMode": "none", "hideFrom": { "legend": false, "tooltip": false, "viz": false }, "lineWidth": 2, "scaleDistribution": { "type": "linear" }, "thresholdsStyle": { "mode": "off" } }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 4 },
      "id": 8,
      "options": { "legend": { "calcs": ["lastNotNull"], "displayMode": "table", "placement": "bottom" }, "tooltip": { "mode": "single" } },
      "targets": [ { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "trades_outcome_total", "legendFormat": "{{reason}}", "refId": "A" } ],
      "title": "Trade Outcomes by Reason",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "palette-classic" }, "custom": { "axisBorderShow": false, "axisCenteredZero": false, "axisLabel": "", "axisWidth": 10, "fillOpacity": 80, "gradientMode": "none", "hideFrom": { "legend": false, "tooltip": false, "viz": false }, "lineWidth": 2, "scaleDistribution": { "type": "linear" }, "thresholdsStyle": { "mode": "off" } }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] }, "unit": "s" }, "overrides": [] },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 12 },
      "id": 9,
      "options": { "legend": { "calcs": ["lastNotNull", "max", "mean"], "displayMode": "table", "placement": "bottom" }, "tooltip": { "mode": "single" } },
      "targets": [
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "histogram_quantile(0.95, sum(rate(llm_call_duration_bucket[5m])) by (le))", "legendFormat": "LLM p95", "refId": "A" },
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "histogram_quantile(0.95, sum(rate(sentiment_analysis_duration_bucket[5m])) by (le))", "legendFormat": "Sentiment p95", "refId": "B" },
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "histogram_quantile(0.95, sum(rate(job_run_duration_bucket[5m])) by (le))", "legendFormat": "Job Run p95", "refId": "C" },
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "histogram_quantile(0.95, sum(rate(data_ingestion_duration_bucket[5m])) by (le))", "legendFormat": "Data Ingest p95", "refId": "D" }
      ],
      "title": "95th Percentile Latencies",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "palette-classic" }, "custom": { "axisBorderShow": false, "axisCenteredZero": false, "axisLabel": "", "axisWidth": 10, "fillOpacity": 80, "gradientMode": "none", "hideFrom": { "legend": false, "tooltip": false, "viz": false }, "lineWidth": 2, "scaleDistribution": { "type": "linear" }, "thresholdsStyle": { "mode": "off" } }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 12 },
      "id": 10,
      "options": { "legend": { "calcs": ["lastNotNull"], "displayMode": "table", "placement": "bottom" }, "tooltip": { "mode": "single" } },
      "targets": [
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "job_runs_completed_total", "legendFormat": "completed", "refId": "A" },
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "job_runs_failed_total", "legendFormat": "failed", "refId": "B" }
      ],
      "title": "Job Run Completion",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "palette-classic" }, "custom": { "axisBorderShow": false, "axisCenteredZero": false, "axisLabel": "", "axisWidth": 10, "fillOpacity": 80, "gradientMode": "none", "hideFrom": { "legend": false, "tooltip": false, "viz": false }, "lineWidth": 2, "scaleDistribution": { "type": "linear" }, "thresholdsStyle": { "mode": "off" } }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 20 },
      "id": 11,
      "options": { "legend": { "calcs": ["lastNotNull"], "displayMode": "table", "placement": "bottom" }, "tooltip": { "mode": "single" } },
      "targets": [ { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "trades_per_symbol_total", "legendFormat": "{{symbol}}", "refId": "A" } ],
      "title": "Trades Per Symbol",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "palette-classic" }, "custom": { "axisBorderShow": false, "axisCenteredZero": false, "axisLabel": "", "axisWidth": 10, "fillOpacity": 80, "gradientMode": "none", "hideFrom": { "legend": false, "tooltip": false, "viz": false }, "lineWidth": 2, "scaleDistribution": { "type": "linear" }, "thresholdsStyle": { "mode": "off" } }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 20 },
      "id": 12,
      "options": { "legend": { "calcs": ["lastNotNull"], "displayMode": "table", "placement": "bottom" }, "tooltip": { "mode": "single" } },
      "targets": [ { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "llm_calls_total", "legendFormat": "{{result}}", "refId": "A" } ],
      "title": "LLM Call Outcomes",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "palette-classic" }, "custom": { "axisBorderShow": false, "axisCenteredZero": false, "axisLabel": "", "axisWidth": 10, "fillOpacity": 80, "gradientMode": "none", "hideFrom": { "legend": false, "tooltip": false, "viz": false }, "lineWidth": 2, "scaleDistribution": { "type": "linear" }, "thresholdsStyle": { "mode": "off" } }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 28 },
      "id": 13,
      "options": { "legend": { "calcs": ["lastNotNull"], "displayMode": "table", "placement": "bottom" }, "tooltip": { "mode": "single" } },
      "targets": [ { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "data_fetch_failures_total", "legendFormat": "{{source}}", "refId": "A" } ],
      "title": "Data Fetch Failures",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "palette-classic" }, "custom": { "axisBorderShow": false, "axisCenteredZero": false, "axisLabel": "", "axisWidth": 10, "fillOpacity": 80, "gradientMode": "none", "hideFrom": { "legend": false, "tooltip": false, "viz": false }, "lineWidth": 2, "scaleDistribution": { "type": "linear" }, "thresholdsStyle": { "mode": "off" } }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 28 },
      "id": 14,
      "options": { "legend": { "calcs": ["lastNotNull"], "displayMode": "table", "placement": "bottom" }, "tooltip": { "mode": "single" } },
      "targets": [
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "sentiment_analyses_completed_total", "legendFormat": "completed", "refId": "A" },
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "sentiment_analyses_failed_total", "legendFormat": "failed", "refId": "B" }
      ],
      "title": "Sentiment Analysis",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" },
      "fieldConfig": { "defaults": { "color": { "mode": "palette-classic" }, "custom": { "axisBorderShow": false, "axisCenteredZero": false, "axisLabel": "", "axisWidth": 10, "fillOpacity": 80, "gradientMode": "none", "hideFrom": { "legend": false, "tooltip": false, "viz": false }, "lineWidth": 2, "scaleDistribution": { "type": "linear" }, "thresholdsStyle": { "mode": "off" } }, "mappings": [], "thresholds": { "mode": "absolute", "steps": [ { "color": "green", "value": null } ] } }, "overrides": [] },
      "gridPos": { "h": 8, "w": 24, "x": 0, "y": 36 },
      "id": 15,
      "options": { "legend": { "calcs": ["lastNotNull"], "displayMode": "table", "placement": "bottom" }, "tooltip": { "mode": "single" } },
      "targets": [
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "hikaricp_connections_active", "legendFormat": "active", "refId": "A" },
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "hikaricp_connections_idle", "legendFormat": "idle", "refId": "B" },
        { "datasource": { "type": "prometheus", "uid": "PBFA97CFB590B2093" }, "expr": "hikaricp_connections_pending", "legendFormat": "pending", "refId": "C" }
      ],
      "title": "Database Connection Pool (HikariCP)",
      "type": "timeseries"
    }
  ],
  "schemaVersion": 39,
  "tags": ["swing-trade", "trading"],
  "templating": { "list": [] },
  "time": { "from": "now-6h", "to": "now" },
  "timepicker": {},
  "timezone": "asia/kolkata",
  "title": "SwingTrade",
  "uid": "swingtrade",
  "version": 1
}
DASHEOF

echo "  SwingTrade dashboard written to /home/dietpi/pi-stack/grafana/provisioning/dashboards/swingtrade.json"
echo ""

# =============================================================================
# 5. Restart Grafana to pick up provisioning changes
# =============================================================================
echo "[5/5] Restarting Grafana..."
docker restart pi-grafana
echo "  Grafana restarted — provisioning will be loaded on startup."
echo ""

# =============================================================================
# Done — next steps
# =============================================================================
echo "=== Setup Complete ==="
echo ""
echo "Next steps:"
echo ""
echo "  1. Wait ~30s for Grafana to fully start, then open:"
echo "     http://piworm.local:3001"
echo ""
echo "  2. Verify Prometheus datasource is connected:"
echo "     Configuration -> Data Sources -> Prometheus -> 'Save & Test'"
echo ""
echo "  3. Check the 'SwingTrade' dashboard (auto-provisioned):"
echo "     Dashboards -> Browse -> SwingTrade"
echo ""
echo "  4. Import community dashboards (manual, via Grafana UI):"
echo "     a. Go to Dashboards -> Import"
echo "     b. Enter dashboard ID: 1860  (Node Exporter Full)"
echo "     c. Enter dashboard ID: 4701  (Spring Boot Micrometer)"
echo "     d. Select 'Prometheus' as datasource, click Import"
echo ""
echo "  5. Verify backend metrics are being scraped:"
echo "     http://piworm.local:9090/targets"
echo "     (swing-trade-stage should be UP)"
echo ""
echo "  6. Check for new metrics in Prometheus:"
echo "     http://piworm.local:9090"
echo "     -> Metrics browser -> type 'trades_' or 'signals_' or 'job_runs'"
echo ""