import axios, { type AxiosInstance } from 'axios'
import { API_BASE_URL, REQUEST_TIMEOUT } from './config'
import type { ApiResponse, Position, Signal, PortfolioSummary, MarketOverview } from './types'

// Create axios instance
const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: REQUEST_TIMEOUT,
  headers: {
    'Accept': 'application/json',
    'Content-Type': 'application/json',
  },
})

// Response interceptor for error handling
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const status = error.response.status
      if (status >= 400 && status < 500) {
        console.error('Client error:', status, error.response.data)
      } else if (status >= 500) {
        console.error('Server error:', status, error.response.data)
      }
    } else if (error.code === 'ECONNABORTED') {
      console.error('Request timeout')
    } else {
      console.error('Network error:', error.message)
    }
    return Promise.reject(error)
  }
)

// Position endpoints
export const getPositionList = (): Promise<ApiResponse<Position[]>> =>
  apiClient.get<ApiResponse<Position[]>>('/positions')

export const getPositionById = (id: string): Promise<ApiResponse<Position>> =>
  apiClient.get<ApiResponse<Position>>(`/positions/${id}`)

export const closePosition = (id: string): Promise<ApiResponse<void>> =>
  apiClient.post<ApiResponse<void>>(`/positions/${id}/close`)

// Signal endpoints
export const getSignalList = (params?: {
  signalType?: 'BUY' | 'SELL' | 'HOLD'
  minConfidence?: number
  limit?: number
}): Promise<ApiResponse<Signal[]>> => apiClient.get<ApiResponse<Signal[]>('/signals', { params })

export const generateSignals = (): Promise<ApiResponse<void>> =>
  apiClient.post<ApiResponse<void>>('/signals/generate')

// Portfolio endpoints
export const getPortfolioSummary = (): Promise<ApiResponse<PortfolioSummary>> =>
  apiClient.get<ApiResponse<PortfolioSummary>>('/portfolio/summary')

export const getEquityCurve = (range?: string): Promise<ApiResponse<{ data: { date: string; value: number }[] }>> =>
  apiClient.get<ApiResponse<{ data: { date: string; value: number }[] }>>('/portfolio/equity-curve', {
    params: { range },
  })

export const getTradeHistory = (limit: number = 10): Promise<ApiResponse<Position[]>> =>
  apiClient.get<ApiResponse<Position[]>>(`/portfolio/trades?limit=${limit}`)

// Market endpoints
export const getMarketOverview = (): Promise<ApiResponse<MarketOverview>> =>
  apiClient.get<ApiResponse<MarketOverview>>('/market/overview')

export default apiClient
