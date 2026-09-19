import { apiRequest } from './shared'
import type {
  PromotionConditionResult,
  PromotionEligibilityResponse,
  PromotionEligibilityStatus,
  StrategyConfig,
  StrategyMode,
} from './types'

const MODES = new Set<StrategyMode>(['OFF', 'BACKTEST_ONLY', 'SHADOW', 'CHAMPION'])
const PROMOTION_STATUSES = new Set<PromotionEligibilityStatus>([
  'ELIGIBLE',
  'NOT_ELIGIBLE',
  'INSUFFICIENT_SAMPLE',
])

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isStrategyConfig(value: unknown): value is StrategyConfig {
  if (!isRecord(value)) return false
  return (
    (value.id === null || typeof value.id === 'number') &&
    typeof value.variantId === 'string' &&
    Number.isInteger(value.version) &&
    typeof value.strategyType === 'string' &&
    isRecord(value.params) &&
    isRecord(value.overlays) &&
    typeof value.paramsHash === 'string' &&
    typeof value.mode === 'string' &&
    MODES.has(value.mode as StrategyMode) &&
    typeof value.paperCapital === 'number' &&
    Number.isFinite(value.paperCapital) &&
    typeof value.current === 'boolean' &&
    (value.notes === null || typeof value.notes === 'string') &&
    typeof value.createdAt === 'string'
  )
}

function validateStrategies(value: unknown): value is StrategyConfig[] {
  if (!Array.isArray(value) || !value.every(isStrategyConfig)) {
    throw new Error('The strategy configuration response was invalid.')
  }
  return true
}

export function getStrategies(signal?: AbortSignal): Promise<StrategyConfig[]> {
  return apiRequest<StrategyConfig[]>('/strategy-configs', {
    method: 'GET',
    responseContract: 'envelope',
    signal,
    validate: validateStrategies,
  })
}

function isPromotionCondition(value: unknown): value is PromotionConditionResult {
  if (!isRecord(value)) return false
  return (
    typeof value.name === 'string' &&
    typeof value.met === 'boolean' &&
    typeof value.actualValue === 'string' &&
    typeof value.threshold === 'string' &&
    (value.note === null || typeof value.note === 'string')
  )
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string')
}

function isPromotionEligibilityResponse(value: unknown): value is PromotionEligibilityResponse {
  if (!isRecord(value)) return false
  return (
    typeof value.challengerVariantId === 'string' &&
    typeof value.championVariantId === 'string' &&
    typeof value.status === 'string' &&
    PROMOTION_STATUSES.has(value.status as PromotionEligibilityStatus) &&
    Array.isArray(value.conditions) &&
    value.conditions.every(isPromotionCondition) &&
    isStringArray(value.notes) &&
    isStringArray(value.dataLimitations)
  )
}

export function fetchPromotionEligibility(
  variantId: string,
  signal?: AbortSignal
): Promise<PromotionEligibilityResponse> {
  return apiRequest<PromotionEligibilityResponse>(
    `/strategy-configs/${encodeURIComponent(variantId)}/promotion-eligibility`,
    {
      method: 'GET',
      responseContract: 'envelope',
      signal,
      validate: isPromotionEligibilityResponse,
    }
  )
}
