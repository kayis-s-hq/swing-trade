import { rawFetch, errResponse } from './shared'
import type {
  ApiResponse,
  SentimentResult,
  SentimentAccuracyStats,
  AccuracySummary,
  AccuracyByWindow,
  AccuracyByRegime,
  AccuracyBySymbol,
  CalibrationData,
  RollingIC,
  SignalVolumeStats,
  ECEStats,
  NewsArticle,
} from './types'

export async function getSentimentLatest(symbol: string): Promise<ApiResponse<SentimentResult>> {
  const raw = await rawFetch(`/sentiment/${symbol}/latest`)
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: SentimentResult; error?: string }
  return { success: true, data: resp.data }
}

export async function getSentimentHistory(
  symbol: string,
  page = 0,
  size = 20
): Promise<ApiResponse<SentimentResult[]>> {
  const raw = await rawFetch(`/sentiment/${symbol}/history?page=${page}&size=${size}`)
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: SentimentResult[]; error?: string }
  return { success: true, data: resp.data }
}

export async function getAccuracyStats(): Promise<ApiResponse<SentimentAccuracyStats>> {
  const raw = await rawFetch('/sentiment/accuracy')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: SentimentAccuracyStats; error?: string }
  return { success: true, data: resp.data }
}

export async function getAccuracySummary(): Promise<ApiResponse<AccuracySummary>> {
  const raw = await rawFetch('/sentiment/accuracy/summary')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: AccuracySummary; error?: string }
  return { success: true, data: resp.data }
}

export async function getAccuracyByWindow(): Promise<ApiResponse<AccuracyByWindow[]>> {
  const raw = await rawFetch('/sentiment/accuracy/by-window')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: AccuracyByWindow[]; error?: string }
  return { success: true, data: resp.data }
}

export async function getAccuracyByRegime(): Promise<ApiResponse<AccuracyByRegime[]>> {
  const raw = await rawFetch('/sentiment/accuracy/by-regime')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: AccuracyByRegime[]; error?: string }
  return { success: true, data: resp.data }
}

export async function getAccuracyBySymbol(): Promise<ApiResponse<AccuracyBySymbol[]>> {
  const raw = await rawFetch('/sentiment/accuracy/by-symbol')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: AccuracyBySymbol[]; error?: string }
  return { success: true, data: resp.data }
}

export async function getCalibration(): Promise<ApiResponse<CalibrationData[]>> {
  const raw = await rawFetch('/sentiment/accuracy/calibration')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: CalibrationData[]; error?: string }
  return { success: true, data: resp.data }
}

export async function getRollingIC(windowDays = 30): Promise<ApiResponse<RollingIC[]>> {
  const raw = await rawFetch(`/sentiment/accuracy/rolling-ic?windowDays=${windowDays}`)
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: RollingIC[]; error?: string }
  return { success: true, data: resp.data }
}

export async function getSignalVolume(): Promise<ApiResponse<SignalVolumeStats>> {
  const raw = await rawFetch('/sentiment/accuracy/signal-volume')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: SignalVolumeStats; error?: string }
  return { success: true, data: resp.data }
}

export async function getECE(): Promise<ApiResponse<ECEStats>> {
  const raw = await rawFetch('/sentiment/accuracy/ece')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: ECEStats; error?: string }
  return { success: true, data: resp.data }
}

export async function triggerSentimentAnalysis(
  symbol: string
): Promise<ApiResponse<SentimentResult>> {
  const raw = await rawFetch(`/sentiment/${symbol}/analyse`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: SentimentResult; error?: string }
  return { success: true, data: resp.data }
}

export async function getLatestNews(symbol: string): Promise<ApiResponse<NewsArticle[]>> {
  const raw = await rawFetch(`/news/${symbol}/latest`)
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: NewsArticle[]; error?: string }
  return { success: true, data: resp.data }
}