import { apiRequest } from './shared'

/** Mirrors backend ParamDefResponse (StrategyConfigController, plan §4.4). */
export interface ParamDef {
  name: string
  type: 'INT' | 'DECIMAL' | 'BOOL' | 'ENUM'
  min: number | null
  max: number | null
  defaultValue: unknown
  description: string | null
  group: string | null
}

export interface StrategyTypeInfo {
  type: string
  params: ParamDef[]
}

export interface StrategyVariant {
  variantId: string
  version: number
  strategyType: string
  params: Record<string, unknown>
  overlays: Record<string, unknown>
  paramsHash: string
  mode: 'OFF' | 'BACKTEST_ONLY' | 'SHADOW' | 'CHAMPION'
  paperCapital: number | string
  isCurrent: boolean
  portfolioAction: 'CONTINUE' | 'RESET' | null
  notes: string | null
  createdAt: string
}

export interface ValidateParamsResult {
  valid: boolean
  errors: string[]
  resolvedParams: Record<string, unknown>
}

export interface BacktestFoldResult {
  fold: number
  windowStart: string
  windowEnd: string
  metrics: Record<string, unknown>
  equityCurve: Array<Record<string, unknown>>
}

export interface BacktestVariantResult {
  variantId: string
  version: number
  strategyType: string
  folds: BacktestFoldResult[]
  deflatedSharpeRatio: number
  trialsUsedForDsr: number
  walkForwardUnstable: boolean
  sharpeStdDevAcrossFolds: number | null
}

export interface BacktestCompareResult {
  variants: BacktestVariantResult[]
}

export interface BacktestCompareRequest {
  variants: Array<{ id: string; version?: number | null }>
  start: string
  end: string
  symbols?: string[] | null
  walkForward?: {
    trainM?: number | null
    testM?: number | null
    stepM?: number | null
    holdoutM?: number | null
    unlockHoldout?: boolean | null
  } | null
  costsOn?: boolean
}

export async function listStrategyTypes(): Promise<StrategyTypeInfo[]> {
  return apiRequest<StrategyTypeInfo[]>('/strategy-types', { responseContract: 'direct' })
}

export async function listCurrentStrategies(): Promise<StrategyVariant[]> {
  return apiRequest<StrategyVariant[]>('/strategies', { responseContract: 'direct' })
}

export async function listStrategyVersions(variantId: string): Promise<StrategyVariant[]> {
  return apiRequest<StrategyVariant[]>(
    `/strategies/${encodeURIComponent(variantId)}/versions`,
    { responseContract: 'direct' }
  )
}

export async function createStrategyVariant(request: {
  variantId: string
  strategyType: string
  params?: Record<string, unknown>
  overlays?: Record<string, unknown>
  paperCapital?: number
  notes?: string
}): Promise<StrategyVariant> {
  return apiRequest<StrategyVariant>('/strategies', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
    responseContract: 'direct',
  })
}

export async function createStrategyVersion(
  variantId: string,
  request: {
    params?: Record<string, unknown>
    overlays?: Record<string, unknown>
    paperCapital?: number
    portfolioAction?: 'CONTINUE' | 'RESET'
    notes?: string
  }
): Promise<StrategyVariant> {
  return apiRequest<StrategyVariant>(
    `/strategies/${encodeURIComponent(variantId)}/versions`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
      responseContract: 'direct',
    }
  )
}

export async function cloneStrategyVariant(
  variantId: string,
  newVariantId: string,
  notes?: string
): Promise<StrategyVariant> {
  return apiRequest<StrategyVariant>(`/strategies/${encodeURIComponent(variantId)}/clone`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ newVariantId, notes }),
    responseContract: 'direct',
  })
}

export async function changeStrategyMode(
  variantId: string,
  mode: StrategyVariant['mode'],
  confirm = false
): Promise<StrategyVariant> {
  return apiRequest<StrategyVariant>(`/strategies/${encodeURIComponent(variantId)}/mode`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ mode, confirm }),
    responseContract: 'direct',
  })
}

export async function validateStrategyParams(
  strategyType: string,
  params: Record<string, unknown>
): Promise<ValidateParamsResult> {
  return apiRequest<ValidateParamsResult>('/strategies/validate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ strategyType, params }),
    responseContract: 'direct',
  })
}

export async function compareBacktests(
  request: BacktestCompareRequest
): Promise<BacktestCompareResult> {
  return apiRequest<BacktestCompareResult>('/backtest/compare', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
    responseContract: 'direct',
  })
}

/** Mirrors backend PromotionEligibilityResponse.Condition (plan §7.4). */
export interface PromotionEligibilityCondition {
  met: boolean
  actualValue: string
  threshold: string
}

/** Mirrors backend PromotionEligibilityResponse (plan §7.4/§8). */
export interface PromotionEligibilityResult {
  challengerVariantId: string
  championVariantId: string
  tenureCalendarDays: number
  status: 'ELIGIBLE' | 'NOT_ELIGIBLE' | 'INSUFFICIENT_SAMPLE'
  tenureAndSampleSize: PromotionEligibilityCondition
  expectancyVsChampion: PromotionEligibilityCondition
  drawdownGuard: PromotionEligibilityCondition
  walkForwardAndOverfitting: PromotionEligibilityCondition
}

export async function fetchPromotionEligibility(
  variantId: string
): Promise<PromotionEligibilityResult> {
  return apiRequest<PromotionEligibilityResult>(
    `/strategies/${encodeURIComponent(variantId)}/promotion-eligibility`,
    { responseContract: 'direct' }
  )
}
