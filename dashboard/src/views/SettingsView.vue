<template>
  <div class="p-6 animate-fade-in">
    <div class="mb-6 flex items-start justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Settings</h1>
        <p class="mt-1 text-sm text-text-muted">Broker connections and trading configuration</p>
      </div>
      <button
        :disabled="saving"
        class="rounded-md bg-brand px-4 py-2 text-sm font-semibold text-brand-text transition-colors hover:bg-brand-hover disabled:opacity-50"
        :class="saved ? 'bg-success' : ''"
        @click="handleSave"
      >
        {{ saving ? 'Saving...' : saved ? 'Saved!' : 'Save All Settings' }}
      </button>
    </div>

    <!-- Tab bar -->
    <div role="tablist" class="mb-6 flex gap-1 border-b border-border-subtle">
      <button
        role="tab"
        aria-label="Broker"
        :aria-selected="activeTab === 'broker'"
        class="px-4 py-2 text-sm font-medium transition-colors"
        :class="
          activeTab === 'broker'
            ? 'border-b-2 border-brand text-brand'
            : 'text-text-muted hover:text-text-primary'
        "
        @click="activeTab = 'broker'"
      >
        Broker
      </button>
      <button
        role="tab"
        aria-label="AI/LLM"
        :aria-selected="activeTab === 'llm'"
        class="px-4 py-2 text-sm font-medium transition-colors"
        :class="
          activeTab === 'llm'
            ? 'border-b-2 border-brand text-brand'
            : 'text-text-muted hover:text-text-primary'
        "
        @click="activeTab = 'llm'"
      >
        AI/LLM
      </button>
      <button
        role="tab"
        aria-label="Trading"
        :aria-selected="activeTab === 'trading'"
        class="px-4 py-2 text-sm font-medium transition-colors"
        :class="
          activeTab === 'trading'
            ? 'border-b-2 border-brand text-brand'
            : 'text-text-muted hover:text-text-primary'
        "
        @click="activeTab = 'trading'"
      >
        Trading
      </button>
      <button
        role="tab"
        aria-label="Health"
        :aria-selected="activeTab === 'health'"
        class="px-4 py-2 text-sm font-medium transition-colors"
        :class="
          activeTab === 'health'
            ? 'border-b-2 border-brand text-brand'
            : 'text-text-muted hover:text-text-primary'
        "
        @click="activeTab = 'health'"
      >
        Health
      </button>
    </div>

    <div class="max-w-2xl">
      <!-- Auth Success/Error Banner -->
      <div
        v-if="authResultBanner"
        class="rounded-lg p-4 text-sm font-medium"
        :class="
          authResultBanner === 'success' ? 'bg-success-bg text-success' : 'bg-danger-bg text-danger'
        "
      >
        {{
          authResultBanner === 'success'
            ? 'Fyers connected successfully!'
            : 'Fyers authentication failed. Please try again.'
        }}
        <button class="ml-2 opacity-60 hover:opacity-100" @click="authResultBanner = null">
          &times;
        </button>
      </div>

      <!-- Broker Tab -->
      <div v-show="activeTab === 'broker'">
        <div class="card-panel p-5">
          <h2 class="mb-4 text-base font-semibold text-text-primary">Broker Connection</h2>

          <!-- Broker Selection -->
          <div class="mb-4 flex gap-3">
            <button
              v-for="b in brokers"
              :key="b.value"
              class="flex-1 rounded-lg border p-3 text-sm font-medium transition-all"
              :class="
                settings.selectedBroker === b.value
                  ? 'border-brand bg-brand-subtle text-brand'
                  : 'border-border-subtle text-text-muted hover:border-border-default hover:text-text-primary'
              "
              @click="settings.selectedBroker = b.value"
            >
              {{ b.label }}
            </button>
          </div>

          <!-- Connection Status -->
          <div
            v-if="settings.selectedBroker === 'fyers'"
            class="rounded-lg border p-4"
            :class="
              fyersConnected
                ? 'border-success/50 bg-success-bg'
                : 'border-border-subtle bg-bg-primary/50'
            "
          >
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <span
                  class="h-2.5 w-2.5 rounded-full"
                  :class="fyersConnected ? 'bg-success pulse-dot' : 'bg-danger'"
                />
                <span
                  class="text-sm font-medium"
                  :class="fyersConnected ? 'text-success' : 'text-text-muted'"
                >
                  {{ fyersConnected ? 'Connected' : 'Disconnected' }}
                </span>
              </div>
              <span v-if="fyersConnected && fyersStatus?.clientId" class="text-xs text-text-muted">
                ID: {{ fyersStatus.clientId }}
              </span>
            </div>
          </div>

          <!-- Connect Button -->
          <div v-if="settings.selectedBroker === 'fyers' && !fyersConnected" class="mt-4 space-y-3">
            <button
              :disabled="authing"
              class="w-full rounded-md bg-brand px-4 py-2.5 text-sm font-semibold text-brand-text transition-colors hover:bg-brand-hover disabled:opacity-50"
              @click="startFyersAuth"
            >
              {{ authing ? 'Opening Fyers...' : 'Connect Fyers Account' }}
            </button>

            <!-- Manual Auth Code -->
            <div v-if="showAuthCodeInput" class="flex gap-2">
              <input
                v-model="authCodeInput"
                placeholder="Paste auth code from browser"
                class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
                @keydown.enter="submitAuthCode"
              />
              <button
                :disabled="authing"
                class="rounded-md border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand/20 disabled:opacity-50"
                @click="submitAuthCode"
              >
                Submit
              </button>
            </div>
            <p v-if="showAuthCodeInput" class="text-xs text-text-muted">
              Complete login on Fyers, then paste the auth code here.
            </p>
          </div>

          <!-- Disconnect -->
          <div v-if="settings.selectedBroker === 'fyers' && fyersConnected" class="mt-4">
            <button
              class="rounded-md border border-danger/30 bg-danger-bg px-4 py-2 text-sm font-medium text-danger transition-colors hover:bg-danger/10"
              @click="disconnectFyers"
            >
              Disconnect
            </button>
          </div>

          <!-- Upstox Placeholder -->
          <div
            v-if="settings.selectedBroker === 'upstox'"
            class="mt-4 rounded-lg border border-border-subtle p-4 text-center"
          >
            <p class="text-sm text-text-muted">Upstox integration coming soon.</p>
          </div>
        </div>
      </div>

      <!-- LLM Tab -->
      <div v-show="activeTab === 'llm'">
        <div class="card-panel p-5">
          <h2 class="mb-4 text-base font-semibold text-text-primary">LLM & Intelligence</h2>

          <!-- Backend Selection -->
          <div class="mb-6">
            <h3 class="text-sm font-medium text-text-secondary mb-3">LLM Backend</h3>
            <div class="flex gap-2 mb-4">
              <button
                v-for="b in llmBackends"
                :key="b.value"
                class="flex-1 rounded-lg border p-3 text-sm font-medium transition-all"
                :class="
                  llmSettings.llmBackend === b.value
                    ? 'border-brand bg-brand-subtle text-brand'
                    : 'border-border-subtle text-text-muted hover:border-border-default hover:text-text-primary'
                "
                @click="llmSettings.llmBackend = b.value"
              >
                {{ b.label }}
              </button>
            </div>

            <!-- Backend descriptions -->
            <div class="space-y-2 text-xs text-text-muted">
              <div v-if="llmSettings.llmBackend === 'local'" class="rounded-md bg-bg-primary p-3">
                llama.cpp server running on the same machine as this app.
              </div>
              <div
                v-else-if="llmSettings.llmBackend === 'pi_ssh'"
                class="rounded-md bg-bg-primary p-3"
              >
                llama.cpp server on Pi (dietpi@piworm). Java starts/stops it via SSH.
                <span class="text-text-secondary">Port:</span> 8089
              </div>
              <div
                v-else-if="llmSettings.llmBackend === 'openai'"
                class="rounded-md bg-bg-primary p-3"
              >
                External OpenAI-compatible LLM endpoint. No server management needed.
              </div>
              <div
                v-else-if="llmSettings.llmBackend === 'mlx'"
                class="rounded-md bg-bg-primary p-3"
              >
                Apple MLX server on local Mac (192.168.1.50). Java starts/stops via Python.
                <span class="text-text-secondary">Port:</span> 8081
              </div>
            </div>
          </div>

          <!-- Local LLM (llama.cpp) -->
          <div v-show="llmSettings.llmBackend === 'local'" class="space-y-4 mb-6">
            <h3 class="text-sm font-medium text-text-secondary">Local LLM (llama.cpp)</h3>

            <div class="flex gap-2">
              <input
                v-model="llmSettings.llmBaseUrl"
                placeholder="http://localhost:8080/v1"
                class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
              />
            </div>

            <div class="flex gap-2">
              <select
                v-model="llmSettings.llamacppModel"
                class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary focus:border-brand focus:outline-none"
              >
                <option value="/home/dietpi/.synapse/models/Qwen3-4B-Instruct-2507-UD-Q4_K_XL.gguf">
                  Qwen3-4B (fast, default)
                </option>
                <option value="/home/dietpi/.synapse/models/google_gemma-4-E2B-it-Q4_0.gguf">
                  Gemma-4 (larger, super analysis)
                </option>
              </select>
              <span class="self-center text-xs text-text-muted">Model</span>
            </div>

            <p class="text-xs text-text-muted">
              Used for sentiment analysis on every signal. Switching models requires restarting the
              llama.cpp service.
            </p>
          </div>

          <!-- Pi SSH LLM -->
          <div v-show="llmSettings.llmBackend === 'pi_ssh'" class="space-y-4 mb-6">
            <h3 class="text-sm font-medium text-text-secondary">Pi SSH LLM (llama.cpp)</h3>
            <p class="text-xs text-text-muted">
              llama.cpp server on Pi 5 (dietpi@piworm). Java starts/stops it via SSH on port 8089.
            </p>
            <div class="flex gap-2">
              <select
                v-model="llmSettings.llamacppModel"
                class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary focus:border-brand focus:outline-none"
              >
                <option value="/home/dietpi/.synapse/models/Qwen3-4B-Instruct-2507-UD-Q4_K_XL.gguf">
                  Qwen3-4B (fast, default)
                </option>
                <option value="/home/dietpi/.synapse/models/google_gemma-4-E2B-it-Q4_0.gguf">
                  Gemma-4 (larger, super analysis)
                </option>
              </select>
              <span class="self-center text-xs text-text-muted">Model</span>
            </div>
            <p class="text-xs text-text-muted">
              Model path on the Pi. Switching models requires restarting the llama.cpp service.
            </p>
            <!-- Server lifecycle -->
            <div class="flex items-center gap-3 pt-2">
              <div class="flex items-center gap-2">
                <span
                  class="h-2.5 w-2.5 rounded-full"
                  :class="piServerRunning ? 'bg-success' : 'bg-danger'"
                />
                <span class="text-xs" :class="piServerRunning ? 'text-success' : 'text-text-muted'">
                  {{ piServerRunning ? 'Running' : 'Stopped' }}
                </span>
              </div>
              <span v-if="piServerStatusMsg" class="text-xs text-text-muted">{{
                piServerStatusMsg
              }}</span>
            </div>
            <div class="flex gap-2 pt-1">
              <button
                v-if="!piServerRunning"
                :disabled="piLoading"
                class="rounded-md bg-brand px-4 py-2 text-sm font-semibold text-brand-text transition-colors hover:bg-brand-hover disabled:opacity-50"
                @click="handlePiStart"
              >
                {{ piLoading ? 'Starting...' : 'Start Server' }}
              </button>
              <button
                v-else
                :disabled="piLoading"
                class="rounded-md border border-danger/30 bg-danger-bg px-4 py-2 text-sm font-medium text-danger transition-colors hover:bg-danger/10 disabled:opacity-50"
                @click="handlePiStop"
              >
                {{ piLoading ? 'Stopping...' : 'Stop Server' }}
              </button>
              <button
                :disabled="piLoading"
                class="rounded-md border border-border-subtle px-4 py-2 text-sm font-medium text-text-muted transition-colors hover:border-border-default hover:text-text-primary disabled:opacity-50"
                @click="refreshPiStatus"
              >
                Refresh
              </button>
            </div>
            <div class="flex gap-2 pt-1">
              <button
                :disabled="testingPi"
                class="rounded-md bg-brand px-4 py-2 text-sm font-semibold text-brand-text transition-colors hover:bg-brand-hover disabled:opacity-50"
                @click="testPiConnection"
              >
                {{ testingPi ? 'Testing...' : 'Test Inference' }}
              </button>
              <span
                v-if="piTestResult"
                class="text-xs"
                :class="piTestSuccess ? 'text-success' : 'text-danger'"
              >
                {{ piTestResult }}
              </span>
            </div>
          </div>

          <!-- OpenAI-compatible LLM (Super Analysis) -->
          <div v-show="llmSettings.llmBackend === 'openai'" class="space-y-4 mb-6">
            <h3 class="text-sm font-medium text-text-secondary">OpenAI-compatible LLM</h3>
            <div class="flex gap-2">
              <input
                v-model="llmSettings.openaiBaseUrl"
                placeholder="https://api.openai.com/v1"
                class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
              />
            </div>

            <div class="flex gap-2">
              <input
                v-model="llmSettings.openaiApiKey"
                type="password"
                placeholder="sk-..."
                class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
              />
              <span class="self-center text-xs text-text-muted">API Key</span>
            </div>

            <div class="flex gap-2">
              <input
                v-model="llmSettings.openaiModel"
                placeholder="gpt-4o"
                class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
              />
              <span class="self-center text-xs text-text-muted">Model</span>
            </div>
            <div class="flex items-center justify-between">
              <p class="text-xs text-text-muted">
                External OpenAI-compatible endpoint for sentiment analysis. No server management
                needed.
              </p>
              <button
                :disabled="testingOpenai"
                class="rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-brand/90 disabled:cursor-not-allowed disabled:opacity-50"
                @click="testOpenAiConnection"
              >
                {{ testingOpenai ? 'Testing...' : 'Test' }}
              </button>
            </div>

            <div
              v-if="openaiTestResult"
              class="text-xs"
              :class="openaiTestSuccess ? 'text-success' : 'text-danger'"
            >
              {{ openaiTestResult }}
            </div>
          </div>

          <!-- MLX LLM -->
          <div v-show="llmSettings.llmBackend === 'mlx'" class="space-y-4 mb-6">
            <h3 class="text-sm font-medium text-text-secondary">MLX Server (Apple Silicon)</h3>
            <p class="text-xs text-text-muted">
              mlx_lm.server on Mac (192.168.1.50). Java starts/stops it via Python on port 8081.
            </p>
            <div class="flex gap-2">
              <select
                v-model="llmSettings.mlxModel"
                class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary focus:border-brand focus:outline-none"
              >
                <option value="Qwen/Qwen2.5-3B-Instruct">Qwen2.5-3B-Instruct (fast, default)</option>
                <option value="Qwen/Qwen2.5-7B-Instruct">Qwen2.5-7B-Instruct (larger, slower)</option>
                <option value="meta-llama/Llama-3.2-3B-Instruct">Llama-3.2-3B-Instruct</option>
                <option value="mistralai/Mistral-7B-Instruct-v0.3">Mistral-7B-Instruct-v0.3</option>
              </select>
              <span class="self-center text-xs text-text-muted">Model</span>
            </div>
            <p class="text-xs text-text-muted">
              Model name on the MLX server. Switching models requires restarting the server.
            </p>
            <!-- Server lifecycle -->
            <div class="flex items-center gap-3 pt-2">
              <div class="flex items-center gap-2">
                <span
                  class="h-2.5 w-2.5 rounded-full"
                  :class="mlxServerRunning ? 'bg-success' : 'bg-danger'"
                />
                <span class="text-xs" :class="mlxServerRunning ? 'text-success' : 'text-text-muted'">
                  {{ mlxServerRunning ? 'Running' : 'Stopped' }}
                </span>
              </div>
              <span v-if="mlxServerStatusMsg" class="text-xs text-text-muted">{{
                mlxServerStatusMsg
              }}</span>
            </div>
            <div class="flex gap-2 pt-1">
              <button
                v-if="!mlxServerRunning"
                :disabled="mlxLoading"
                class="rounded-md bg-brand px-4 py-2 text-sm font-semibold text-brand-text transition-colors hover:bg-brand-hover disabled:opacity-50"
                @click="handleMlxStart"
              >
                {{ mlxLoading ? 'Starting...' : 'Start Server' }}
              </button>
              <button
                v-else
                :disabled="mlxLoading"
                class="rounded-md border border-danger/30 bg-danger-bg px-4 py-2 text-sm font-medium text-danger transition-colors hover:bg-danger/10 disabled:opacity-50"
                @click="handleMlxStop"
              >
                {{ mlxLoading ? 'Stopping...' : 'Stop Server' }}
              </button>
              <button
                :disabled="mlxLoading"
                class="rounded-md border border-border-subtle px-4 py-2 text-sm font-medium text-text-muted transition-colors hover:border-border-default hover:text-text-primary disabled:opacity-50"
                @click="refreshMlxStatus"
              >
                Refresh
              </button>
            </div>
            <div class="flex gap-2 pt-1">
              <button
                :disabled="mlxTesting"
                class="rounded-md bg-brand px-4 py-2 text-sm font-semibold text-brand-text transition-colors hover:bg-brand-hover disabled:opacity-50"
                @click="testMlxConnection"
              >
                {{ mlxTesting ? 'Testing...' : 'Test Inference' }}
              </button>
              <span
                v-if="mlxTestResult"
                class="text-xs"
                :class="mlxTestSuccess ? 'text-success' : 'text-danger'"
              >
                {{ mlxTestResult }}
              </span>
            </div>
          </div>

          <!-- PDF Extraction -->
          <div class="space-y-4 mb-6">
            <h3 class="text-sm font-medium text-text-secondary">PDF Extraction (Pi 5)</h3>
            <div class="flex gap-2">
              <input
                v-model="llmSettings.pdfBaseUrl"
                placeholder="http://pi5-ip:8080"
                class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
              />
              <button
                :disabled="testingPdf"
                class="rounded-md border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand/20 disabled:opacity-50"
                @click="testPdfExtraction"
              >
                {{ testingPdf ? 'Testing...' : 'Test' }}
              </button>
            </div>

            <div class="flex gap-2">
              <input
                v-model="llmSettings.pdfModel"
                placeholder="gemma-4-E2B"
                class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
              />
              <span class="self-center text-xs text-text-muted">Model name</span>
            </div>
          </div>

          <!-- Discord Configuration -->
          <div class="space-y-4">
            <h3 class="text-sm font-medium text-text-secondary">Discord Notifications</h3>
            <div
              class="flex items-center justify-between rounded-lg border border-border-subtle p-3"
            >
              <span class="text-sm">Enable Discord</span>
              <label class="relative inline-flex items-center cursor-pointer">
                <input v-model="discordSettings.enabled" type="checkbox" class="sr-only peer" />
                <div
                  class="w-9 h-5 bg-gray-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-brand"
                />
              </label>
            </div>
            <div class="flex gap-2">
              <input
                v-model="discordSettings.webhookUrl"
                placeholder="https://discord.com/api/webhooks/..."
                class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
              />
              <button
                :disabled="testingDiscord"
                class="rounded-md border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand/20 disabled:opacity-50"
                @click="testDiscordWebhook"
              >
                {{ testingDiscord ? 'Testing...' : 'Test' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Trading Tab -->
      <div v-show="activeTab === 'trading'">
        <div class="card-panel p-5">
          <h2 class="mb-4 text-base font-semibold text-text-primary">Trading Configuration</h2>
          <div class="grid grid-cols-2 gap-4 text-sm">
            <div class="rounded-lg border border-border-subtle p-3">
              <p class="text-xs text-text-muted">Mode</p>
              <select
                v-model="settings.tradingConfig.mode"
                class="mt-1 w-full rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary focus:border-brand focus:outline-none"
              >
                <option value="paper">Paper Trading</option>
                <option value="live">Live Trading</option>
              </select>
            </div>
            <div class="rounded-lg border border-border-subtle p-3">
              <p class="text-xs text-text-muted">Max Position Size</p>
              <div class="mt-1 flex items-center gap-1">
                <input
                  v-model.number="settings.tradingConfig.maxPositionSize"
                  type="number"
                  min="1"
                  max="100"
                  class="w-20 rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary focus:border-brand focus:outline-none"
                />
                <span class="text-xs text-text-muted">%</span>
              </div>
            </div>
            <div class="rounded-lg border border-border-subtle p-3">
              <p class="text-xs text-text-muted">Stop Loss</p>
              <div class="mt-1 flex items-center gap-1">
                <input
                  v-model.number="settings.tradingConfig.stopLoss"
                  type="number"
                  min="1"
                  max="50"
                  class="w-20 rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary focus:border-brand focus:outline-none"
                />
                <span class="text-xs text-text-muted">%</span>
              </div>
            </div>
            <div class="rounded-lg border border-border-subtle p-3">
              <p class="text-xs text-text-muted">Take Profit</p>
              <div class="mt-1 flex items-center gap-1">
                <input
                  v-model.number="settings.tradingConfig.takeProfit"
                  type="number"
                  min="1"
                  max="200"
                  class="w-20 rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary focus:border-brand focus:outline-none"
                />
                <span class="text-xs text-text-muted">%</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Health Tab -->
      <div v-show="activeTab === 'health'">
        <div class="card-panel p-5">
          <h2 class="mb-4 text-base font-semibold text-text-primary">System Health</h2>
          <div v-if="healthStatus" class="space-y-2">
            <div
              v-for="(comp, key) in healthStatus.components"
              :key="key"
              class="flex items-center justify-between rounded-lg border border-border-subtle/50 p-3"
            >
              <span class="text-sm font-medium text-text-secondary">{{ key }}</span>
              <span
                class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium"
                :class="healthColor(comp.status)"
              >
                <span class="h-1.5 w-1.5 rounded-full" :class="healthDot(comp.status)" />
                {{ comp.status }}
              </span>
            </div>
          </div>
          <div v-else class="flex items-center justify-center py-8">
            <LoadingSpinner :message="'Checking system...'" :small="true" />
          </div>
        </div>
      </div>
    </div>

    <!-- Save Button -->
    <button
      :disabled="saving"
      class="w-full rounded-md bg-brand px-4 py-3 text-sm font-semibold text-brand-text transition-colors hover:bg-brand-hover disabled:opacity-50"
      :class="saved ? 'bg-success' : ''"
      @click="handleSave"
    >
      {{ saving ? 'Saving...' : saved ? 'Saved!' : 'Save All Settings' }}
    </button>

    <!-- Toast notifications -->
    <Transition
      enter-active-class="transition ease-out duration-200"
      enter-from-class="opacity-0 translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition ease-in duration-150"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 translate-y-2"
    >
      <Toast v-if="toastVisible" :message="toastMessage" :type="toastType" :duration="4000" />
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getFyersLoginUrl,
  getFyersStatus,
  fyersAuthCode,
  fyersLogout,
  testDiscordWebhook as apiTestDiscordWebhook,
  testPiConnection as apiTestPiConnection,
  testOpenAiConnection as apiTestOpenAiConnection,
  startPiServer as apiStartPiServer,
  stopPiServer as apiStopPiServer,
  getPiServerStatus as apiGetPiServerStatus,
  startMlxServer as apiStartMlxServer,
  stopMlxServer as apiStopMlxServer,
  getMlxServerStatus as apiGetMlxServerStatus,
  testMlxConnection as apiTestMlxConnection,
} from '../api/client'
import type { FyersStatus, HealthStatus } from '../api/types'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import Toast from '../components/Toast.vue'
import {
  getSettings,
  loadSettings,
  saveSettings,
  saveLlmSettings,
  saveDiscordSettings,
} from '../stores/settings'

const activeTab = ref('broker')
const settings = getSettings()
const llmSettings = settings.llmSettings
const discordSettings = settings.discordSettings
const fyersStatus = ref<FyersStatus | null>(null)
const fyersConnected = computed(() => fyersStatus.value?.connected ?? false)
const healthStatus = ref<HealthStatus | null>(null)
const authResultBanner = ref<'success' | 'error' | null>(null)

const authing = ref(false)
const showAuthCodeInput = ref(false)
const authCodeInput = ref('')
const testingPdf = ref(false)
const testingDiscord = ref(false)
const testingPi = ref(false)
const testingOpenai = ref(false)
const mlxTesting = ref(false)
const mlxLoading = ref(false)
const mlxServerRunning = ref(false)
const mlxServerStatusMsg = ref('')
const mlxTestResult = ref('')
const mlxTestSuccess = ref(false)
const piTestResult = ref('')
const piTestSuccess = ref(false)
const piServerRunning = ref(false)
const piServerStatusMsg = ref('')
const piLoading = ref(false)
const openaiTestResult = ref('')
const openaiTestSuccess = ref(false)
const saving = ref(false)
const saved = ref(false)
const toastMessage = ref('')
const toastType = ref<'success' | 'error' | 'warning' | 'info'>('info')
const toastVisible = ref(false)
let pollTimer: number | null = null

const brokers = [
  { value: 'fyers' as const, label: 'Fyers' },
  { value: 'upstox' as const, label: 'Upstox' },
  { value: 'yahoo' as const, label: 'Yahoo Finance' },
  { value: 'none' as const, label: 'None (Read Only)' },
]

const llmBackends = [
  { value: 'local' as const, label: 'Local' },
  { value: 'pi_ssh' as const, label: 'Pi SSH' },
  { value: 'openai' as const, label: 'OpenAI' },
  { value: 'mlx' as const, label: 'MLX' },
]

const healthColor = (status: string) => {
  if (status === 'UP') return 'bg-success-bg text-success'
  if (status === 'DOWN') return 'bg-danger-bg text-danger'
  if (status === 'DEGRADED') return 'bg-warning-bg text-warning'
  return 'bg-bg-hover text-text-muted'
}

const healthDot = (status: string) => {
  if (status === 'UP') return 'bg-success'
  if (status === 'DOWN') return 'bg-danger'
  if (status === 'DEGRADED') return 'bg-warning'
  return 'bg-text-muted'
}

const refreshFyersStatus = async () => {
  const res = await getFyersStatus()
  if (res.success && res.data) fyersStatus.value = res.data
}

const pollFyersStatus = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  pollTimer = window.setInterval(async () => {
    try {
      const res = await getFyersStatus()
      if (res.success && res.data && res.data.connected) {
        if (pollTimer) {
          clearInterval(pollTimer)
          pollTimer = null
        }
        authResultBanner.value = 'success'
        showAuthCodeInput.value = false
        fyersStatus.value = res.data
      }
    } catch {
      // ignore polling errors
    }
  }, 2000) as unknown as number
}

const refreshHealth = async () => {
  const { getHealthStatus } = await import('../api/client')
  const res = await getHealthStatus()
  if (res.success && res.data) healthStatus.value = res.data
}

const startFyersAuth = async () => {
  authing.value = true
  try {
    const res = await getFyersLoginUrl()
    if (!res.success || !res.data) throw new Error(res.error ?? 'Failed to get login URL')

    const popup = window.open(
      res.data.url,
      'fyers-auth',
      'width=600,height=700,left=' +
        Math.round(window.screen.width / 2 - 300) +
        ',top=' +
        Math.round(window.screen.height / 2 - 350)
    )
    if (!popup) {
      authResultBanner.value = 'error'
      authing.value = false
      throw new Error('Popup blocked. Please allow popups for this site.')
    }

    pollFyersStatus()

    popup.addEventListener('load', () => {
      if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
      }
    })
  } catch (err: unknown) {
    authResultBanner.value = 'error'
  } finally {
    authing.value = false
  }
}

const submitAuthCode = async () => {
  if (!authCodeInput.value.trim()) return
  authing.value = true
  try {
    const res = await fyersAuthCode(authCodeInput.value.trim())
    if (!res.success) throw new Error(res.error ?? 'Auth failed')
    fyersStatus.value = res.data ?? null
    showAuthCodeInput.value = false
    authCodeInput.value = ''
  } catch (err: unknown) {
    alert(err instanceof Error ? err.message : 'Auth failed')
  } finally {
    authing.value = false
  }
}

const disconnectFyers = async () => {
  const res = await fyersLogout()
  fyersStatus.value = res.success && res.data ? res.data : null
}

const handleSave = async () => {
  saving.value = true
  saved.value = false
  try {
    const ok = await saveSettings()
    if (ok) {
      saved.value = true
      toastMessage.value = 'Settings saved!'
      toastType.value = 'success'
      toastVisible.value = true
      setTimeout(() => {
        saved.value = false
      }, 2000)
      setTimeout(() => {
        toastVisible.value = false
      }, 4000)
    }
  } catch (err: unknown) {
    toastMessage.value = err instanceof Error ? err.message : 'Failed to save settings'
    toastType.value = 'error'
    toastVisible.value = true
    setTimeout(() => {
      toastVisible.value = false
    }, 4000)
  } finally {
    saving.value = false
  }
}

const testPdfExtraction = async () => {
  testingPdf.value = true
  try {
    const ok = await saveLlmSettings()
    if (ok) {
      toastMessage.value = 'PDF extraction settings saved.'
      toastType.value = 'success'
      toastVisible.value = true
      setTimeout(() => {
        toastVisible.value = false
      }, 4000)
    } else {
      toastMessage.value = 'Failed to save PDF settings.'
      toastType.value = 'error'
      toastVisible.value = true
      setTimeout(() => {
        toastVisible.value = false
      }, 4000)
    }
  } catch (err: unknown) {
    toastMessage.value = err instanceof Error ? err.message : 'Failed to save PDF settings'
    toastType.value = 'error'
    toastVisible.value = true
    setTimeout(() => {
      toastVisible.value = false
    }, 4000)
  } finally {
    testingPdf.value = false
  }
}

const testDiscordWebhook = async () => {
  const ok = await saveDiscordSettings()
  if (!ok) {
    toastMessage.value = 'Failed to save Discord settings.'
    toastType.value = 'error'
    toastVisible.value = true
    setTimeout(() => {
      toastVisible.value = false
    }, 4000)
    return
  }
  testingDiscord.value = true
  try {
    const res = await apiTestDiscordWebhook()
    if (res.success && res.data?.success) {
      toastMessage.value = 'Discord webhook test successful!'
      toastType.value = 'success'
      toastVisible.value = true
      setTimeout(() => {
        toastVisible.value = false
      }, 4000)
    } else {
      toastMessage.value = res.error ?? 'Discord webhook test failed'
      toastType.value = 'error'
      toastVisible.value = true
      setTimeout(() => {
        toastVisible.value = false
      }, 4000)
    }
  } catch (err: unknown) {
    toastMessage.value = err instanceof Error ? err.message : 'Discord webhook test failed'
    toastType.value = 'error'
    toastVisible.value = true
    setTimeout(() => {
      toastVisible.value = false
    }, 4000)
  } finally {
    testingDiscord.value = false
  }
}

const refreshPiStatus = async () => {
  try {
    const res = await apiGetPiServerStatus()
    if (res.success && res.data) {
      piServerRunning.value = res.data.running ?? false
      piServerStatusMsg.value = res.data.message ?? ''
    }
  } catch {
    // ignore
  }
}

const handlePiStart = async () => {
  piLoading.value = true
  try {
    const res = await apiStartPiServer()
    if (res.success && res.data) {
      piServerRunning.value = res.data.running ?? false
      piServerStatusMsg.value = res.data.message ?? ''
      toastMessage.value = res.data.message ?? ''
      toastType.value = res.data.success ? 'success' : 'error'
      toastVisible.value = true
      setTimeout(() => {
        toastVisible.value = false
      }, 4000)
    }
  } catch (err: unknown) {
    toastMessage.value = err instanceof Error ? err.message : 'Failed to start Pi server'
    toastType.value = 'error'
    toastVisible.value = true
    setTimeout(() => {
      toastVisible.value = false
    }, 4000)
  } finally {
    piLoading.value = false
  }
}

const handlePiStop = async () => {
  piLoading.value = true
  try {
    const res = await apiStopPiServer()
    if (res.success && res.data) {
      piServerRunning.value = res.data.running ?? false
      piServerStatusMsg.value = res.data.message ?? ''
      toastMessage.value = res.data.message ?? ''
      toastType.value = res.data.success ? 'success' : 'error'
      toastVisible.value = true
      setTimeout(() => {
        toastVisible.value = false
      }, 4000)
    }
  } catch (err: unknown) {
    toastMessage.value = err instanceof Error ? err.message : 'Failed to stop Pi server'
    toastType.value = 'error'
    toastVisible.value = true
    setTimeout(() => {
      toastVisible.value = false
    }, 4000)
  } finally {
    piLoading.value = false
  }
}

const testPiConnection = async () => {
  testingPi.value = true
  piTestResult.value = ''
  piTestSuccess.value = false
  try {
    const res = await apiTestPiConnection()
    if (res.success && res.data) {
      piTestResult.value = res.data.message ?? (res.data.success ? 'Connected!' : 'Failed to start')
      piTestSuccess.value = res.data.success
      if (res.data.success) {
        toastMessage.value = 'Pi SSH connection successful — llama-server started on Pi'
        toastType.value = 'success'
        toastVisible.value = true
        setTimeout(() => {
          toastVisible.value = false
        }, 4000)
        refreshPiStatus()
      } else {
        toastMessage.value = 'Pi connected but llama-server failed to start'
        toastType.value = 'warning'
        toastVisible.value = true
        setTimeout(() => {
          toastVisible.value = false
        }, 4000)
      }
    } else {
      piTestResult.value = res.error ?? 'Test failed'
      piTestSuccess.value = false
      toastMessage.value = piTestResult.value
      toastType.value = 'error'
      toastVisible.value = true
      setTimeout(() => {
        toastVisible.value = false
      }, 4000)
    }
  } catch (err: unknown) {
    piTestResult.value = err instanceof Error ? err.message : 'Network error'
    piTestSuccess.value = false
    toastMessage.value = piTestResult.value
    toastType.value = 'error'
    toastVisible.value = true
    setTimeout(() => {
      toastVisible.value = false
    }, 4000)
  } finally {
    testingPi.value = false
  }
}

const testOpenAiConnection = async () => {
  testingOpenai.value = true
  openaiTestResult.value = ''
  openaiTestSuccess.value = false
  try {
    const res = await apiTestOpenAiConnection()
    if (res.success && res.data) {
      openaiTestResult.value = res.data.message ?? (res.data.success ? 'Connected!' : 'Failed')
      openaiTestSuccess.value = res.data.success
      if (res.data.success) {
        toastMessage.value = 'OpenAI-compatible LLM responded successfully'
        toastType.value = 'success'
        toastVisible.value = true
        setTimeout(() => {
          toastVisible.value = false
        }, 4000)
      } else {
        toastMessage.value = 'LLM responded but unexpected output'
        toastType.value = 'warning'
        toastVisible.value = true
        setTimeout(() => {
          toastVisible.value = false
        }, 4000)
      }
    } else {
      openaiTestResult.value = res.error ?? 'Test failed'
      openaiTestSuccess.value = false
      toastMessage.value = openaiTestResult.value
      toastType.value = 'error'
      toastVisible.value = true
      setTimeout(() => {
        toastVisible.value = false
      }, 4000)
    }
  } catch (err: unknown) {
    openaiTestResult.value = err instanceof Error ? err.message : 'Network error'
    openaiTestSuccess.value = false
    toastMessage.value = openaiTestResult.value
    toastType.value = 'error'
    toastVisible.value = true
    setTimeout(() => {
      toastVisible.value = false
    }, 4000)
  } finally {
    testingOpenai.value = false
  }
}

const refreshMlxStatus = async () => {
  try {
    const res = await apiGetMlxServerStatus()
    if (res.success && res.data) {
      mlxServerRunning.value = res.data.running ?? false
      mlxServerStatusMsg.value = res.data.message ?? ''
    }
  } catch {
    // ignore
  }
}

const handleMlxStart = async () => {
  mlxLoading.value = true
  try {
    const res = await apiStartMlxServer()
    if (res.success && res.data) {
      mlxServerRunning.value = res.data.running ?? false
      mlxServerStatusMsg.value = res.data.message ?? ''
      toastMessage.value = res.data.message ?? ''
      toastType.value = res.data.success ? 'success' : 'error'
      toastVisible.value = true
      setTimeout(() => {
        toastVisible.value = false
      }, 4000)
    }
  } catch (err: unknown) {
    toastMessage.value = err instanceof Error ? err.message : 'Failed to start MLX server'
    toastType.value = 'error'
    toastVisible.value = true
    setTimeout(() => {
      toastVisible.value = false
    }, 4000)
  } finally {
    mlxLoading.value = false
  }
}

const handleMlxStop = async () => {
  mlxLoading.value = true
  try {
    const res = await apiStopMlxServer()
    if (res.success && res.data) {
      mlxServerRunning.value = res.data.running ?? false
      mlxServerStatusMsg.value = res.data.message ?? ''
      toastMessage.value = res.data.message ?? ''
      toastType.value = res.data.success ? 'success' : 'error'
      toastVisible.value = true
      setTimeout(() => {
        toastVisible.value = false
      }, 4000)
    }
  } catch (err: unknown) {
    toastMessage.value = err instanceof Error ? err.message : 'Failed to stop MLX server'
    toastType.value = 'error'
    toastVisible.value = true
    setTimeout(() => {
      toastVisible.value = false
    }, 4000)
  } finally {
    mlxLoading.value = false
  }
}

const testMlxConnection = async () => {
  mlxTesting.value = true
  mlxTestResult.value = ''
  mlxTestSuccess.value = false
  try {
    const res = await apiTestMlxConnection()
    if (res.success && res.data) {
      mlxTestResult.value = res.data.message ?? (res.data.success ? 'Connected!' : 'Failed to start')
      mlxTestSuccess.value = res.data.success
      if (res.data.success) {
        toastMessage.value = 'MLX connection successful — server started and responded to inference'
        toastType.value = 'success'
        toastVisible.value = true
        setTimeout(() => {
          toastVisible.value = false
        }, 4000)
        refreshMlxStatus()
      } else {
        toastMessage.value = 'MLX connected but server failed to start'
        toastType.value = 'warning'
        toastVisible.value = true
        setTimeout(() => {
          toastVisible.value = false
        }, 4000)
      }
    } else {
      mlxTestResult.value = res.error ?? 'Test failed'
      mlxTestSuccess.value = false
      toastMessage.value = mlxTestResult.value
      toastType.value = 'error'
      toastVisible.value = true
      setTimeout(() => {
        toastVisible.value = false
      }, 4000)
    }
  } catch (err: unknown) {
    mlxTestResult.value = err instanceof Error ? err.message : 'Network error'
    mlxTestSuccess.value = false
    toastMessage.value = mlxTestResult.value
    toastType.value = 'error'
    toastVisible.value = true
    setTimeout(() => {
      toastVisible.value = false
    }, 4000)
  } finally {
    mlxTesting.value = false
  }
}

const handleMessage = (event: MessageEvent) => {
  if (event.data?.type === 'fyers_auth_success') {
    authResultBanner.value = 'success'
    showAuthCodeInput.value = false
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    refreshFyersStatus()
  } else if (event.data?.type === 'fyers_auth_error') {
    authResultBanner.value = 'error'
    authing.value = false
    showAuthCodeInput.value = false
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }
}

onMounted(async () => {
  const route = useRoute()
  const router = useRouter()
  const authParam = (route?.query?.auth as string) ?? null
  if (authParam === 'success' || authParam === 'error') {
    authResultBanner.value = authParam
    router.replace({ query: {} })
  }
  refreshFyersStatus()
  refreshHealth()
  refreshPiStatus()
  refreshMlxStatus()
  await loadSettings()
  window.addEventListener('message', handleMessage)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  window.removeEventListener('message', handleMessage)
})
</script>
