import { apiRequest } from './shared'
import type { TodayHolidayStatus, HolidayListResponse } from './types'

export async function getTodayHolidayStatus(): Promise<TodayHolidayStatus> {
  return apiRequest<TodayHolidayStatus>('/holidays/today', { responseContract: 'envelope' })
}

export async function getUpcomingHolidays(): Promise<HolidayListResponse> {
  return apiRequest<HolidayListResponse>('/holidays', { responseContract: 'envelope' })
}
