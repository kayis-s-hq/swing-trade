import { rawFetch, errResponse } from './shared'
import type { ApiResponse, BacktestResult, BacktestReportSummary } from './types'

export async function runBacktest(
  symbol: string,
  exchange: string = 'NSE'
): Promise<ApiResponse<BacktestResult>> {
  const params = new URLSearchParams({ symbol: symbol.toUpperCase().trim(), exchange })
  const raw = await rawFetch(`/backtest/run?${params}`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as BacktestResult }
}

export async function runBacktestAll(
  exchange: string = 'NSE'
): Promise<ApiResponse<BacktestReportSummary>> {
  const raw = await rawFetch(`/backtest/run-all?exchange=${exchange}`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as BacktestReportSummary }
}

export async function listBacktestReports(): Promise<ApiResponse<string[]>> {
  const raw = await rawFetch('/backtest/reports')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as string[] }
}

export async function getBacktestReport(
  filename: string
): Promise<ApiResponse<BacktestReportSummary>> {
  const raw = await rawFetch(`/backtest/reports/${filename}`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as BacktestReportSummary }
}