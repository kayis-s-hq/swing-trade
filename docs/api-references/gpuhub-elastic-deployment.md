# GPUHUB Elastic Deployment API

Base URL: `https://www.gpuhub.com/api/v1/dev`
Auth: Bearer token in `Authorization` header (raw JWT, no "Bearer" prefix)

## Endpoints

### List Private Images
`POST /api/v1/dev/image/private/list`

**Body:** `page_index` (int), `page_size` (int)
**Response:** `code`, `data.list` [{ `id`, `image_name`, `image_uuid` }]

### Create Deployment
`POST /api/v1/dev/deployment`

**Body:** `name`, `deployment_type` (ReplicaSet/Job/Container), `replica_num`, `parallelism_num` (Job only), `reuse_container`, `reuse_container_scope`, `service_6006_port_protocol`, `service_6008_port_protocol`, `container_template`
**Response:** `data.deployment_uuid`

**ContainerTemplate fields:**
- `dc_list` — datacenter names (Singapore-B ↔ Singpore-A swap)
- `gpu_name_set` — GPU model names (e.g. "RTX 4080 (Super)", "RTX 5090", "RTX Pro 6000")
- `gpu_num` — number of GPUs
- `cuda_v_from/to` — three-digit integers (11.8 → 118)
- `cpu_num_from/to`, `memory_size_from/to`, `price_from/to` — ranges
- `image_uuid` — full UUID from private list (e.g. `image-695984f6d2`)
- `cmd` — startup command
- `cmd_before_shutdown` — optional pre-shutdown command

### List Deployments
`POST /api/v1/dev/deployment/list`

**Body:** `page_index`, `page_size`, optional `name`, `status` (running/stopped), `deployment_uuid`
**Response:** Paginated `{ list, result_total, ... }` with deployment specs, running/stopped counts, timestamps, `price_estimates`

### Scale Replicas
`PUT /api/v1/dev/deployment/replica_num`

**Body:** `deployment_uuid`, `replica_num` (int > 0)
**Response:** `code`, `msg`

### Stop Entire Deployment
`PUT /api/v1/dev/deployment/operate`

**Body:** `deployment_uuid`, `operation` ("stop" or "delete")
**Response:** `code`, `msg`

### Delete Entire Deployment
`DELETE /api/v1/dev/deployment`

**Body:** `deployment_uuid`
**Response:** `code`, `msg`
**Note:** Automatically stops running instances first.

### List Container Events
`POST /api/v1/dev/deployment/container/event/list`

**Body:** `deployment_uuid`, optional `deployment_container_uuid`, `page_index`, `page_size`, `offset`
**Response:** Array of events with `deployment_container_uuid`, `status`, `created_at`

### List Containers
`POST /api/v1/dev/deployment/container/list`

**Body:** `deployment_uuid`, optional filters (`container_uuid`, `date_from/to`, `gpu_name`, CPU/memory/price ranges, `released`, `status` array), `page_index`, `page_size`, `offset`
**Response:** Array of container objects with `uuid`, `data_center`, `status`, specs, `price`, timestamps, `info` (ssh_command, root_password, service URLs)

### Stop a Container
`PUT /api/v1/dev/deployment/container/stop`

**Body:** `deployment_container_uuid`, `decrease_one_replica_num` (bool), `no_cache` (bool), optional `cmd_before_shutdown`
**Response:** `code`, `msg`

### Host Blacklist
`POST /api/v1/dev/deployment/blacklist`

**Body:** `deployment_container_uuid`, `expire_in_minutes` (default 1440, max 43200), optional `comment`
**Response:** `code`, `msg`

### Get Active Blacklist
`GET /api/v1/dev/deployment/blacklist`
**Response:** Array of entries with `created_at`, `updated_at`, `data_center`, `expired_time`, `machine_id`, `msg`

### Real-time GPU Stock
`POST /api/v1/dev/machine/region/gpu_stock`

**Body:** `region_sign`, optional filters (`cuda_v_from/to`, `gpu_name_set`, CPU/memory/price ranges)
**Response:** Array mapping GPU models to `idle_gpu_num` and `total_gpu_num`

### Check Duration Package Balance
`GET /api/v1/dev/deployment/ddp/overview?deployment_uuid=...`
**Response:** Array of prepaid packages with `gpu_type`, `total` seconds, `balance` seconds, `dc_list`

## Appendix

### Regions
"Singapore-B" and "Singpore-A" are swapped in `dc_list` values (typo intentional).

### CUDA
Three-digit integers: 11.8 → `118`, 12.0 → `120`.

### Env Vars (inside container)
- `AutoDLContainerUUID` — container UUID
- `AutoDLDeploymentUUID` — deployment UUID
- `AutoDLDataCenter` — datacenter name

### Available GPU Models
- RTX 4080 (Super)
- RTX 5090
- RTX Pro 6000