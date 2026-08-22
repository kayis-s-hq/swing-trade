# Context MCP — Documentation Lookup

Use the **Context** MCP server on the Pi to query version-specific documentation for 17 installed libraries. This is the preferred source for library docs before web searching.

## MCP Tools

### `get_docs` — Query installed library docs
```json
{
  "library": "name@version",
  "topic": "short keyword or API name"
}
```
- `library`: Installed library to search (name@version). See package list below.
- `topic`: Short API name, keyword, or phrase (e.g., `'cors middleware'`). Search terms are all matched together — extra words narrow but can also eliminate results.
- If the library is not installed, use `search_packages` then `download_package`, then retry `get_docs`.

### `search_packages` — Search registry for available packages
```json
{
  "registry": "npm | pip | cargo | go",
  "name": "short package name",
  "version": "specific version (optional)",
  "server": "server name (optional)"
}
```
- Use short package names like `'react'`, `'next'`, or `'fastapi'`.
- If you find a match, call `download_package`, then retry `get_docs`.
- If the registry package is unavailable or insufficient, ask the user to run `context add` to build docs from source.

### `download_package` — Install a package from the registry
```json
{
  "registry": "npm | pip | cargo | go",
  "name": "package name",
  "version": "version (e.g., '18.3.1', '15.0.4')",
  "server": "server name (optional)"
}
```
- Once installed, retry `get_docs` against the installed `name@version` for instant offline lookup.

## Installed Packages (17 total, ~34 MB)

### Java / Spring Boot
| Package | Version | Source | Sections |
|---|---|---|---|
| `java/spring-boot` | 3.5.7 | spring-projects/spring-boot `v3.5.7` | 819 |
| `java/spring-ai` | 1.1.8 | spring-projects/spring-ai `v1.1.8` | 1,081 |
| `java/spring-data` | 4.1.0 | spring-projects/spring-data-jpa `4.1.0` | 101 |
| `java/gradle` | 8.9.0 | gradle/gradle `v8.9.0` | 1,969 |
| `java/resilience4j` | 2.4.0 | resilience4j/resilience4j `v2.4.0` | 58 |
| `flyway/flyway` | 12.9.0 | flyway/flyway `flyway-12.9.0` | 32 |

### AI / LLM
| Package | Version | Source | Sections |
|---|---|---|---|
| `js/langchain4j` | 1.18.1 | langchain4j/langchain4j `1.18.1` | 1,101 |
| `js/langchain` | 0.1.16 | langchain-ai/langchain `v0.1.16` | 1,205 |
| `js/openai` | 1.99.9 | openai/openai-python `v1.99.9` | 56 |

### Vue Frontend
| Package | Version | Source | Sections |
|---|---|---|---|
| `vue` | latest | npm (`npm/vue`) | 746 |
| `tailwindcss` | latest | npm (`npm/tailwindcss`) | 838 |
| `js/vue-router` | 4.6.0 | vuejs/router `v4.6.0` | 117 |
| `pinia` | 2.2.8 | npm (`npm/pinia`) | 228 |

### Testing
| Package | Version | Source | Sections |
|---|---|---|---|
| `vitest` | 3.2.7 | npm (`npm/vitest`) | 728 |
| `playwright` | 1.8.1 | npm (`npm/playwright`) | 600 |

### Infrastructure
| Package | Version | Source | Sections |
|---|---|---|---|
| `js/docker` | 18.09-release | docker/docs `v18.09-release` | 4,615 |
| `js/docker-compose` | 5.4.0 | docker/compose `v5.4.0` | 100 |

## Usage Workflow

1. **Query installed docs**: Use `get_docs` with the exact `library@version` and a short topic.
2. **Library missing from registry?**: Use `search_packages` to find it, then `download_package` to install, then retry `get_docs`.
3. **Not on any registry?**: Ask the user to run `context add <source> --name <category>/<name>` to build docs from source.

## Examples

```
get_docs: { library: "java/spring-boot@3.5.7", topic: "REST controller" }
get_docs: { library: "java/resilience4j@2.4.0", topic: "CircuitBreaker" }
get_docs: { library: "vue@latest", topic: "Composition API setup" }
get_docs: { library: "js/docker@18.09-release", topic: "multi-stage build" }
search_packages: { registry: "npm", name: "express" }
download_package: { registry: "npm", name: "express", version: "4.21.0" }
```

## Server Details

- Container: `pi-context`
- MCP endpoint: `https://context.pi.local/mcp`
- Local: `http://localhost:8080/mcp`
- Data volume: `context_data` (cached SQLite docs)
- Memory limit: 256M
- Project: https://github.com/neuledge/context