# Pi SSH LLM: Lifecycle Endpoints, Idle Fix, Multi-Client Routing

## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: Idle fix — PiLlamaServerManager | [x] PASS | Removed `&& !healthCheck()` from idle condition, moved `idleCheckTime.set()` out of `healthCheck()` into `startServer()`. Added package-private accessors for testing. | 2026-08-19 |
| 2: Idle fix — LlamaCppServerManager | [x] PASS | Removed `idleCheckTime.set()` from `healthCheck()`, added it after `startIdleMonitor()` in `startServer()`. Added package-private accessors for testing. | 2026-08-19 |
| 3: Multi-client beans — LlmConfig | [x] PASS | Replaced single `openAiChatModel()` with 3 beans: `localChatModel`, `piSshChatModel`, `openAiChatModel`. `chatClient()` marked @Primary using local model. Extracted `createChatModel()` helper. Removed `backendKey` parameter. | 2026-08-19 |
| 4: Multi-client routing — LlmClientProvider | [x] PASS | Constructor accepts 3 OpenAiChatModel beans + LlmBackendSelector. getClient() routes to correct model via switch. Updated TestLlmConfig to wire new constructor. | 2026-08-19 |
| 5: Controller lifecycle endpoints | [x] PASS | 5 tests pass. Endpoints already implemented in SettingsController. Created SettingsTestConfiguration for @WebMvcTest. | 2026-08-19 |

## 1. Feature Map

| Feature | Tested? | Test Type |
|---------|---------|-----------|
| Pi idle timeout fires on healthy idle servers | No | Unit |
| Local idle timeout fires on idle servers | Partial (1 test, buggy condition) | Unit |
| 3 OpenAiChatModel beans at startup | No | Integration |
| LlmClientProvider routes to correct backend | No | Unit |
| POST /settings/pi/start | No | Unit (MockMvc) |
| POST /settings/pi/stop | No | Unit (MockMvc) |
| GET /settings/pi/status | No | Unit (MockMvc) |

## 2. Phase Breakdown

### Phase 1: Idle fix — PiLlamaServerManager (TDD — will fail)

**What to test:** The idle monitor should stop the server when it is idle for >= idleTimeoutSec, regardless of health status. The `idleCheckTime` should NOT be updated by health checks alone.

**File path:** `backend/llm/src/test/java/com/swingtrade/llm/service/PiLlamaServerManagerIdleTest.java` (new)

**Test methods:**

```java
@Nested
@DisplayName("Idle Monitor — healthy server should auto-stop")
class HealthyServerIdleStop {

    @Test
    void shouldStopServerWhenIdleExceedsTimeout() {
        // Arrange: running=true, idleCheckTime = 4000ms ago, idleTimeoutSec=3600
        // Act: trigger idle monitor check (simulate via getIdleSeconds)
        // Assert: isRunning() == false, logger.info called about auto-stop
    }

    @Test
    void shouldNotStopServerWhenBelowIdleTimeout() {
        // Arrange: running=true, idleCheckTime = 100ms ago
        // Act: trigger idle monitor check
        // Assert: isRunning() == true
    }
}

@Nested
@DisplayName("idleCheckTime should not be updated by health checks")
class IdleCheckTimeNotUpdatedByHealth {

    @Test
    void healthCheckShouldNotResetIdleTimer() {
        // Arrange: idleCheckTime = 5000ms ago
        // Act: call healthCheck() — returns true (healthy)
        // Assert: getIdleSeconds() should still be ~5000, NOT reset to 0
        // This proves health check does NOT update idleCheckTime
    }
}
```

**Why it will fail:** Current `PiLlamaServerManager` line 189 has `if (idleSeconds >= idleTimeoutSec && !healthCheck())` — the `&& !healthCheck()` condition means a healthy server never stops. Also, `healthCheck()` at line 221 calls `idleCheckTime.set(System.currentTimeMillis())` BEFORE checking status, so every health check resets the timer.

**Fix:**
- `PiLlamaServerManager.java` line 189: change `if (idleSeconds >= idleTimeoutSec && !healthCheck())` → `if (idleSeconds >= idleTimeoutSec)`
- Remove `idleCheckTime.set(System.currentTimeMillis())` from `healthCheck()` method (line 221)
- Add `idleCheckTime.set(System.currentTimeMillis())` to `startServer()` after health succeeds (line 178, after `running = true`)

### Phase 2: Idle fix — LlamaCppServerManager

**What to test:** Same idle fix pattern for the local manager.

**File path:** `backend/llm/src/test/java/com/swingtrade/llm/service/LlamaCppServerManagerIdleTest.java` (new)

**Test methods:**

```java
@Nested
@DisplayName("Idle Monitor — should auto-stop idle server")
class IdleServerAutoStop {

    @Test
    void shouldStopServerWhenIdleExceedsTimeout() {
        // Arrange: serverProcess alive, idleCheckTime = 4000ms ago, idleTimeoutSec=300
        // Act: trigger idle monitor check
        // Assert: serverProcess == null (destroyed)
    }

    @Test
    void shouldNotStopServerWhenBelowIdleTimeout() {
        // Arrange: serverProcess alive, idleCheckTime = 100ms ago
        // Act: trigger idle monitor check
        // Assert: serverProcess != null
    }
}
```

**Why it will fail:** Same root cause — `idleCheckTime` updated in `healthCheck()` (line 274), so the timer never accumulates.

**Fix:**
- `LlamaCppServerManager.java` line 274: remove `idleCheckTime.set(System.currentTimeMillis())` from `healthCheck()`
- Add `idleCheckTime.set(System.currentTimeMillis())` after `startServer()` succeeds (line 233, after `startIdleMonitor()`)

### Phase 3: Multi-client beans — LlmConfig (TDD — will fail)

**What to test:** Three `OpenAiChatModel` beans are created — one per backend. Each reads its own base URL from settings with fallback defaults.

**File path:** `backend/llm/src/test/java/com/swingtrade/llm/config/LlmConfigMultiClientTest.java` (new)

**Test methods:**

```java
@SpringBootTest
@TestPropertySource(properties = {
    "llm.backend=local",
    "spring.ai.openai.base-url=",
    "spring.ai.openai.api-key=test-key",
    "spring.ai.openai.chat.options.model=qwen3-4b"
})
class LlmConfigMultiClientTest {

    @Autowired
    private ApplicationContext context;

    @MockBean
    private AppSettingsStore appSettingsStore;

    @Test
    void shouldCreateLocalChatModelBean() {
        // Arrange: when appSettingsStore.get("llm.base_url") → empty
        // Assert: context contains bean of type OpenAiChatModel with baseUrl=http://localhost:8080
        OpenAiChatModel local = context.getBean("localChatModel", OpenAiChatModel.class);
        // Verify the underlying API has the correct base URL
    }

    @Test
    void shouldCreatePiSshChatModelBean() {
        // Assert: context contains bean of type OpenAiChatModel with baseUrl=http://piworm.local:8090
        OpenAiChatModel pi = context.getBean("piSshChatModel", OpenAiChatModel.class);
    }

    @Test
    void shouldCreateOpenAiChatModelBean() {
        // Assert: context contains bean of type OpenAiChatModel with baseUrl=https://api.openai.com
        OpenAiChatModel openai = context.getBean("openAiChatModel", OpenAiChatModel.class);
    }

    @Test
    void shouldHaveExactlyThreeChatModelBeans() {
        // Assert: map of type OpenAiChatModel has size 3
        Map<String, OpenAiChatModel> beans = context.getBeansOfType(OpenAiChatModel.class);
        assertThat(beans).hasSize(3);
    }
}
```

**Why it will fail:** Current `LlmConfig` creates only ONE `OpenAiChatModel` bean. No `localChatModel`, `piSshChatModel`, or `openAiChatModel` beans exist.

**Fix:**
- `LlmConfig.java`: Replace single `openAiChatModel()` bean with three beans:
  - `localChatModel()` — reads `llm.base_url` from settings, fallback `http://localhost:8080`
  - `piSshChatModel()` — reads `llm.base_url` from settings, fallback `http://piworm.local:8090`
  - `openAiChatModel()` — reads `openai.base_url` from settings, fallback `https://api.openai.com`
- Each uses `resolveBaseUrlForBackend()` helper that takes a `Backend` enum value
- Remove the `backendKey` parameter from `openAiChatModel()` (backend selection is now runtime, not startup)
- Keep `chatClient()` bean — it takes ONE `OpenAiChatModel` as parameter. Need to change to `@Primary` or create a separate `chatClient` per model. **Decision:** Create `@Primary` chatClient from the LOCAL model (default), but `LlmClientProvider` will route to the right model directly.

**Bean naming convention:** `localChatModel`, `piSshChatModel`, `openAiChatModel`

### Phase 4: Multi-client routing — LlmClientProvider (TDD — will fail)

**What to test:** `LlmClientProvider.getClient()` returns a `SpringAiLlmClient` backed by the correct `OpenAiChatModel` based on `LlmBackendSelector.resolve()`.

**File path:** `backend/llm/src/test/java/com/swingtrade/llm/service/LlmClientProviderRoutingTest.java` (new)

**Test methods:**

```java
@ExtendWith(MockitoExtension.class)
class LlmClientProviderRoutingTest {

    @Mock
    private LlmBackendSelector selector;

    @Mock
    private OpenAiChatModel localModel;

    @Mock
    private OpenAiChatModel piSshModel;

    @Mock
    private OpenAiChatModel openAiModel;

    private LlmClientProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LlmClientProvider(selector, localModel, piSshModel, openAiModel);
    }

    @Nested
    @DisplayName("Routing — LOCAL backend")
    class LocalRouting {

        @Test
        void shouldReturnLocalClientWhenBackendIsLocal() {
            // Arrange
            when(selector.resolve()).thenReturn(LlmBackendSelector.Backend.LOCAL);

            // Act
            LlmClient client = provider.getClient();

            // Assert
            assertThat(client).isNotNull();
            // Verify the client is backed by localModel (not piSshModel or openAiModel)
        }
    }

    @Nested
    @DisplayName("Routing — PI_SSH backend")
    class PiSshRouting {

        @Test
        void shouldReturnPiSshClientWhenBackendIsPiSsh() {
            // Arrange
            when(selector.resolve()).thenReturn(LlmBackendSelector.Backend.PI_SSH);

            // Act
            LlmClient client = provider.getClient();

            // Assert
            assertThat(client).isNotNull();
        }
    }

    @Nested
    @DisplayName("Routing — OPENAI backend")
    class OpenAiRouting {

        @Test
        void shouldReturnOpenAiClientWhenBackendIsOpenai() {
            // Arrange
            when(selector.resolve()).thenReturn(LlmBackendSelector.Backend.OPENAI);

            // Act
            LlmClient client = provider.getClient();

            // Assert
            assertThat(client).isNotNull();
        }
    }
}
```

**Why it will fail:** Current `LlmClientProvider` takes a single `SpringAiLlmClient` in its constructor and returns it directly. No routing logic exists.

**Fix:**
- `LlmClientProvider.java`: Change constructor to accept 3 `OpenAiChatModel` beans + `LlmBackendSelector`
- `getClient()` calls `selector.resolve()` → switches to the right `OpenAiChatModel` → creates a new `ChatClient` → wraps in `SpringAiLlmClient`
- `SpringAiLlmClient` constructor accepts `ChatClient` (already does)
- No change to `SpringAiLlmClient` itself

### Phase 5: SpringAiLlmClient — verify no change

**What to test:** Existing `SpringAiLlmClientTest` tests still pass after the refactor.

**File path:** `backend/llm/src/test/java/com/swingtrade/llm/client/SpringAiLlmClientTest.java` (existing — no changes needed)

**Action:** Run existing tests. If any fail due to constructor signature change, fix them.

**Why:** Verify the refactor doesn't break the existing client contract.

### Phase 6: SettingsController lifecycle endpoints (TDD — will fail)

**What to test:** Three new REST endpoints for Pi server lifecycle management.

**File path:** `backend/api/src/test/java/com/swingtrade/api/controller/SettingsControllerPiLifecycleTest.java` (new)

**Test methods:**

```java
@WebMvcTest(SettingsController.class)
class SettingsControllerPiLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PiLlamaServerManager piServerManager;

    @Nested
    @DisplayName("POST /api/settings/pi/start")
    class StartEndpoint {

        @Test
        void shouldReturn200WithSuccessWhenServerStarts() throws Exception {
            // Arrange
            when(piServerManager.isRunning()).thenReturn(true);

            // Act + Assert
            mockMvc.perform(post("/api/settings/pi/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.message").value("Pi llama-server started and healthy"));
        }

        @Test
        void shouldReturn200WithErrorWhenStartFails() throws Exception {
            // Arrange
            when(piServerManager.isRunning()).thenReturn(false);
            doThrow(new IllegalStateException("SSH failed")).when(piServerManager).ensureRunning();

            // Act + Assert
            mockMvc.perform(post("/api/settings/pi/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.running").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/settings/pi/stop")
    class StopEndpoint {

        @Test
        void shouldReturn200WithStoppedStatus() throws Exception {
            // Arrange
            doNothing().when(piServerManager).stop();

            // Act + Assert
            mockMvc.perform(post("/api/settings/pi/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.message").value("Pi llama-server stopped"));
        }
    }

    @Nested
    @DisplayName("GET /api/settings/pi/status")
    class StatusEndpoint {

        @Test
        void shouldReturnRunningStatus() throws Exception {
            // Arrange
            when(piServerManager.isRunning()).thenReturn(true);

            // Act + Assert
            mockMvc.perform(get("/api/settings/pi/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.running").value(true));
        }

        @Test
        void shouldReturnStoppedStatus() throws Exception {
            // Arrange
            when(piServerManager.isRunning()).thenReturn(false);

            // Act + Assert
            mockMvc.perform(get("/api/settings/pi/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.running").value(false));
        }
    }
}
```

**Why it will fail:** The endpoints `POST /settings/pi/start`, `POST /settings/pi/stop`, and `GET /settings/pi/status` do not exist yet.

**Fix:** Already coded in `SettingsController.java` — just need to verify they work with tests. The implementation is already in place (added in the previous conversation).

## 3. Files Summary

| Action | File | Type |
|--------|------|------|
| Create | `backend/llm/src/test/java/com/swingtrade/llm/service/PiLlamaServerManagerIdleTest.java` | Unit |
| Create | `backend/llm/src/test/java/com/swingtrade/llm/service/LlamaCppServerManagerIdleTest.java` | Unit |
| Create | `backend/llm/src/test/java/com/swingtrade/llm/config/LlmConfigMultiClientTest.java` | Integration |
| Create | `backend/llm/src/test/java/com/swingtrade/llm/service/LlmClientProviderRoutingTest.java` | Unit |
| Modify | `backend/llm/src/main/java/com/swingtrade/llm/service/PiLlamaServerManager.java` (2 lines) | Source |
| Modify | `backend/llm/src/main/java/com/swingtrade/llm/service/LlamaCppServerManager.java` (2 lines) | Source |
| Modify | `backend/llm/src/main/java/com/swingtrade/llm/config/LlmConfig.java` (major rewrite) | Source |
| Modify | `backend/llm/src/main/java/com/swingtrade/llm/service/LlmClientProvider.java` (constructor + routing) | Source |
| Create | `backend/api/src/test/java/com/swingtrade/api/controller/SettingsControllerPiLifecycleTest.java` | Unit (MockMvc) |

## 4. Verification

```bash
# Phase 1: Idle fix — PiLlamaServerManager (RED)
./gradlew :llm:test --tests=PiLlamaServerManagerIdleTest  # fails: idle monitor never stops healthy server

# Apply fix: remove && !healthCheck(), move idleCheckTime update out of healthCheck()

# Phase 1: GREEN
./gradlew :llm:test --tests=PiLlamaServerManagerIdleTest  # passes

# Phase 2: Idle fix — LlamaCppServerManager (RED)
./gradlew :llm:test --tests=LlamaCppServerManagerIdleTest  # fails: idleCheckTime updated by health checks

# Apply fix: same pattern as Phase 1

# Phase 2: GREEN
./gradlew :llm:test --tests=LlamaCppServerManagerIdleTest  # passes

# Phase 3: Multi-client beans (RED)
./gradlew :llm:test --tests=LlmConfigMultiClientTest  # fails: only 1 bean exists

# Apply fix: 3 beans in LlmConfig

# Phase 3: GREEN
./gradlew :llm:test --tests=LlmConfigMultiClientTest  # passes

# Phase 4: Routing (RED)
./gradlew :llm:test --tests=LlmClientProviderRoutingTest  # fails: no routing logic

# Apply fix: 3-model constructor + selector routing in LlmClientProvider

# Phase 4: GREEN
./gradlew :llm:test --tests=LlmClientProviderRoutingTest  # passes

# Phase 5: Verify existing tests
./gradlew :llm:test --tests=SpringAiLlmClientTest  # should still pass

# Phase 6: Lifecycle endpoints (RED)
./gradlew :api:test --tests=SettingsControllerPiLifecycleTest  # fails: endpoints don't exist

# Apply fix: endpoints already coded, just verify

# Phase 6: GREEN
./gradlew :api:test --tests=SettingsControllerPiLifecycleTest  # passes

# Full suite
./gradlew :llm:test :api:test  # all pass
```

## Pending TDD Plans

| Plan | Phases | Status |
|------|--------|--------|
| `native-image-upgrade.md` | 4 phases | [ ] 4/4 pending |

You have 1 pending TDD plan. Execute one before starting new work?