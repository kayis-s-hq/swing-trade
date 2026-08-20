// Re-export all API functions for backward compatibility
// New code should import from domain-specific modules directly.

// Shared infrastructure
export { rawFetch, unwrap, toNum, errResponse } from './shared'

// Health
export { checkHealth, getHealthStatus } from './health'

// Signals
export {
  getSignals,
  generateAllSignals,
  generateAllSignalsStream,
  clearAllSignals,
  clearSignalsForSymbol,
  getSignalsByType,
  getLatestSignalForSymbol,
  getHighConfidenceSignals,
  generatePriceActionSignal,
  triggerScan,
} from './signals'
export type { SignalGenerationProgress } from './signals'

// Positions
export {
  getPortfolioSummary,
  getMarketOverview,
  getPositions,
  getClosedPositions,
  getTradeHistory,
  closePosition,
  executeTrade,
  getEquityCurve,
} from './positions'
export type { ExecuteTradeParams } from './positions'

// Watchlist
export {
  getWatchlist,
  addToWatchlist,
  removeFromWatchlist,
  toggleWatchlistActive,
} from './watchlist'

// Ingestion
export {
  getIngestionStatus,
  triggerDataPull,
  getPullProgress,
  cancelDataPull,
} from './ingestion'

// Settings
export {
  getSettings,
  setBroker,
  getLlmSettings,
  setLlmSettings,
  getDiscordSettings,
  setDiscordSettings,
  testDiscordWebhook,
  testPiConnection,
  testOpenAiConnection,
  startPiServer,
  stopPiServer,
  getPiServerStatus,
  getGpuHubSettings,
  setGpuHubSettings,
  getTradingSettings,
  setTradingSettings,
  saveAllSettings,
} from './settings'

// Sentiment
export {
  getSentimentLatest,
  getSentimentHistory,
  getAccuracyStats,
  getAccuracySummary,
  getAccuracyByWindow,
  getAccuracyByRegime,
  getAccuracyBySymbol,
  getCalibration,
  getRollingIC,
  getSignalVolume,
  getECE,
  triggerSentimentAnalysis,
  getLatestNews,
} from './sentiment'

// Backtest
export {
  runBacktest,
  runBacktestAll,
  listBacktestReports,
  getBacktestReport,
} from './backtest'

// Analysis
export {
  getCompositeAnalysis,
  runFullAnalysis,
} from './analysis'

// Job
export {
  startJobRun,
  getJobRunProgress,
  getJobRunSummary,
  listJobRuns,
  cancelJobRun,
} from './job'

// Holidays
export {
  getTodayHolidayStatus,
  getUpcomingHolidays,
} from './holidays'

// Fyers
export {
  getFyersLoginUrl,
  fyersAuthCode,
  getFyersStatus,
  fyersLogout,
} from './fyers'

// Backfill
export { backfillSymbol } from './backfill'

// Types
export * from './types'