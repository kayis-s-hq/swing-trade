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
  apiClient.get('/positions').then((res) => res.data as ApiResponse<Position[]>)

export const getPositionById = (id: string): Promise<ApiResponse<Position>> =>
  apiClient.get(`/positions/${id}`).then((res) => res.data as ApiResponse<Position>)

export const closePosition = (id: string): Promise<ApiResponse<void>> =>
  apiClient.post(`/positions/${id}/close`).then((res) => res.data as ApiResponse<void>)

// Signal endpoints
export const getSignalList = (params?: {
  signalType?: 'BUY' | 'SELL' | 'HOLD'
  minConfidence?: number
  limit?: number
}): Promise<ApiResponse<Signal[]>> => apiClient.get('/signals', { params }).then((res) => res.data as ApiResponse<Signal[]>)

export const generateSignals = (): Promise<ApiResponse<void>> =>
  apiClient.post('/signals/generate').then((res) => res.data as ApiResponse<void>)

// Portfolio endpoints
export const getPortfolioSummary = (): Promise<ApiResponse<PortfolioSummary>> =>
  apiClient.get('/portfolio/summary').then((res) => res.data as ApiResponse<PortfolioSummary>)

export const getEquityCurve = (range?: string): Promise<ApiResponse<{ data: { date: string; value: number }[] }>> =>
  apiClient.get('/portfolio/equity-curve', { params: { range } }).then((res) => res.data as ApiResponse<{ data: { date: string; value: number }[] }>)

export const getTradeHistory = (limit: number = 10): Promise<ApiResponse<Position[]>> =>
  apiClient.get(`/portfolio/trades?limit=${limit}`).then((res) => res.data as ApiResponse<Position[]>)

// Market endpoints
export const getMarketOverview = (): Promise<ApiResponse<MarketOverview>> =>
  apiClient.get('/market/overview').then((res) => res.data as ApiResponse<MarketOverview>)

export default apiClient
