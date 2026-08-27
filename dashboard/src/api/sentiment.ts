import { apiRequest } from './shared'
import type {
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

const request = <T>(path: string, init?: RequestInit) =>
  apiRequest<T>(path, { ...init, signal: init?.signal ?? undefined, responseContract: 'envelope' })

export const getSentimentLatest = (symbol: string) =>
  request<SentimentResult>(`/sentiment/${encodeURIComponent(symbol)}/latest`)
export const getSentimentHistory = (symbol: string, page = 0, size = 20) =>
  request<SentimentResult[]>(
    `/sentiment/${encodeURIComponent(symbol)}/history?page=${page}&size=${size}`
  )
export const getAccuracyStats = () => request<SentimentAccuracyStats>('/sentiment/accuracy')
export const getAccuracySummary = () => request<AccuracySummary>('/sentiment/accuracy/summary')
export const getAccuracyByWindow = () =>
  request<AccuracyByWindow[]>('/sentiment/accuracy/by-window')
export const getAccuracyByRegime = () =>
  request<AccuracyByRegime[]>('/sentiment/accuracy/by-regime')
export const getAccuracyBySymbol = () =>
  request<AccuracyBySymbol[]>('/sentiment/accuracy/by-symbol')
export const getCalibration = () => request<CalibrationData[]>('/sentiment/accuracy/calibration')
export const getRollingIC = (windowDays = 30) =>
  request<RollingIC[]>(`/sentiment/accuracy/rolling-ic?windowDays=${windowDays}`)
export const getSignalVolume = () => request<SignalVolumeStats>('/sentiment/accuracy/signal-volume')
export const getECE = () => request<ECEStats>('/sentiment/accuracy/ece')
export const triggerSentimentAnalysis = (symbol: string) =>
  request<SentimentResult>(`/sentiment/${encodeURIComponent(symbol)}/analyse`, { method: 'POST' })
export const getLatestNews = (symbol: string) =>
  request<NewsArticle[]>(`/news/${encodeURIComponent(symbol)}/latest`)
