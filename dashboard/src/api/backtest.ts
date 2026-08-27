import { apiRequest } from './shared'
import type { BacktestResult, BacktestReportSummary } from './types'

export async function runBacktest(
  symbol: string,
  exchange: string = 'NSE'
): Promise<BacktestResult> {
  const params = new URLSearchParams({ symbol: symbol.toUpperCase().trim(), exchange })
  return apiRequest<BacktestResult>(`/backtest/run?${params}`, {
    method: 'POST',
    responseContract: 'direct',
  })
}

export async function runBacktestAll(exchange: string = 'NSE'): Promise<BacktestReportSummary> {
  return apiRequest<BacktestReportSummary>(`/backtest/run-all?exchange=${exchange}`, {
    method: 'POST',
    responseContract: 'direct',
  })
}

export async function listBacktestReports(): Promise<string[]> {
  return apiRequest<string[]>('/backtest/reports', {
    responseContract: 'direct',
  })
}

export async function getBacktestReport(filename: string): Promise<BacktestReportSummary> {
  return apiRequest<BacktestReportSummary>(`/backtest/reports/${encodeURIComponent(filename)}`, {
    responseContract: 'direct',
  })
}
