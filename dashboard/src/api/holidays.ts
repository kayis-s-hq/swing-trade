import { rawFetch, errResponse } from './shared'
import type { ApiResponse, TodayHolidayStatus, HolidayListResponse } from './types'

export async function getTodayHolidayStatus(): Promise<ApiResponse<TodayHolidayStatus>> {
  const raw = await rawFetch('/holidays/today')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: TodayHolidayStatus; error?: string }
  return { success: true, data: resp.data }
}

export async function getUpcomingHolidays(): Promise<ApiResponse<HolidayListResponse>> {
  const raw = await rawFetch('/holidays')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: HolidayListResponse; error?: string }
  return { success: true, data: resp.data }
}