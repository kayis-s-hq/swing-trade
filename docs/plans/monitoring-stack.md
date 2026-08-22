# Monitoring Stack Plan

## Status: ALL DONE

### 1. Fix Promtail — DONE
- Config fixed: removed invalid `host_networks` field, fixed `source_labels` YAML list format, updated network regex to `pi-stack_monitoring`, added container name filter
- Prometheus scrape targets: swing-trade-stage-api, node, grafana, caddy, vllm
- User confirmed: Promtail working cleanly, 12,245+ log entries sent, no more errors

### 2. Expand Micrometer metrics — DONE
**Files created** (all in `core/src/main/java/com/swingtrade/core/metrics/`):
- `SignalMetrics.java` — counters: `signals.generated`, `signals.filtered`, `signals.by_type`
- `LlmMetrics.java` — timer: `llm.call.duration`, counters: `llm.calls` (success/failure), `llm.sentiment.analyzed`
- `DataIngestionMetrics.java` — counter: `data.candles_ingested`, timer: `data.ingestion.duration`, counter: `data.fetch_failures`
- `JobOrchestratorMetrics.java` — counters: `job.runs.completed`, `job.runs.failed`, timer: `job.run.duration`, gauge: `job.runs.active`
- `KillSwitchMetrics.java` — gauge: `killswitch.active`, counter: `killswitch.state_changed`
- `SentimentMetrics.java` — counters: `sentiment.analyses.completed/failed`, timer: `sentiment.analysis.duration`

**Removed**: `ApiTradeMetrics.java` (duplicate), `TradeMetricsTest.java` in api module

**Services injected**:
- `PaperTradingEngine` → `TradeMetrics` (trade open/close)
- `PriceActionSignalEngine` → `SignalMetrics` (signal generation)
- `DataIngestionService` → `DataIngestionMetrics` (candle save/fetch failure)
- `KillSwitchService` → `KillSwitchMetrics` (enable/disable)
- `JobOrchestratorService` → `JobOrchestratorMetrics` (run start/complete/fail)
- `SentimentService` → `LlmMetrics` + `SentimentMetrics` (LLM call/sentiment analysis)

**Core module compiles clean.** Pre-existing data module errors (100, FyersClass/HoldingModel) unrelated.

### 3. Prometheus config — DONE
- Updated `infra/monitoring/prometheus.yml` — removed unreachable `swing-trade-dev` job
- Config provided for pi-node: write to `/home/dietpi/pi-stack/prometheus/prometheus.yml`, then `docker exec pi-prometheus curl -X POST http://localhost:9090/-/reload`

### 4. Grafana dashboards — DONE
- `swingtrade.json` created with 15 panels:
  - Stats: Active Positions, Kill Switch, Active Job Runs, Total Trades Opened, Total Signals, Candles Ingested
  - Timeseries: Trade & Signal Rate, Trade Outcomes, 95th Percentile Latencies, Job Run Completion, Trades Per Symbol, LLM Call Outcomes, Data Fetch Failures, Sentiment Analysis, HikariCP Pool
- Provisioning files created in `infra/monitoring/provisioning/dashboards/`

### 5. Grafana datasources — DONE
- `infra/monitoring/provisioning/datasources/prometheus.yml` — Prometheus datasource config
- `infra/monitoring/provisioning/dashboards/dashboards.yml` — dashboard provider config
- Grafana already has `./grafana/provisioning` mounted to `/etc/grafana/provisioning` on pi-node

## Remaining actions for user

### Prometheus config (pi-node)
```bash
ssh dietpi@piworm.local 'cat > /home/dietpi/pi-stack/prometheus/prometheus.yml << '\''EOF'\''
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
EOF'
ssh dietpi@piworm.local "docker exec pi-prometheus curl -X POST http://localhost:9090/-/reload"
```

### Grafana provisioning (pi-node)
```bash
# Create directory structure
ssh dietpi@piworm.local "mkdir -p /home/dietpi/pi-stack/grafana/provisioning/datasources /home/dietpi/pi-stack/grafana/provisioning/dashboards"

# Copy datasource config
ssh dietpi@piworm.local 'cat > /home/dietpi/pi-stack/grafana/provisioning/datasources/prometheus.yml << '\''EOF'\''
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
EOF'

# Copy dashboard provider config
ssh dietpi@piworm.local 'cat > /home/dietpi/pi-stack/grafana/provisioning/dashboards/dashboards.yml << '\''EOF'\''
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
EOF'

# Copy SwingTrade dashboard (user must scp the JSON file)
scp /Users/kayisrahman/Documents/workspace/ideas/swing-trade/.claude/worktrees/monitoring/infra/monitoring/provisioning/dashboards/swingtrade.json dietpi@piworm.local:/home/dietpi/pi-stack/grafana/provisioning/dashboards/swingtrade.json
```

Then restart Grafana:
```bash
ssh dietpi@piworm.local "docker restart pi-grafana"
```

### Import standard dashboards (manual via Grafana UI)
After Grafana restarts, import these community dashboards:
- **Node Exporter Full** (ID: 1860) — CPU, memory, disk, network, load
- **Spring Boot Micrometer** (ID: 4701) — JVM, HTTP requests, system, logback

### Verify
1. Prometheus targets: `http://piworm.local:9090/targets` — all UP
2. Grafana: `http://piworm.local:3001` — check Prometheus datasource connected, SwingTrade dashboard loaded
3. Backend metrics: run app with `local,fyers` profile, check `/actuator/prometheus` for new metrics