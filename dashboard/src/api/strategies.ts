import { apiRequest } from './shared'
import type {
  PromotionConditionResult,
  PromotionEligibilityResponse,
  PromotionEligibilityStatus,
  StrategyConfig,
  StrategyConfigRequest,
  StrategyMode,
  StrategyTypeInfo,
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

function isStrategyTypeInfo(value: unknown): value is StrategyTypeInfo {
  if (!isRecord(value) || typeof value.type !== 'string') return false
  const schema = value.paramSchema
  if (!isRecord(schema) || !Array.isArray(schema.params)) return false
  return schema.params.every(
    (param) => isRecord(param) && typeof param.name === 'string' && typeof param.type === 'string'
  )
}

function validateStrategyTypes(value: unknown): value is StrategyTypeInfo[] {
  return Array.isArray(value) && value.every(isStrategyTypeInfo)
}

export function getStrategyTypes(signal?: AbortSignal): Promise<StrategyTypeInfo[]> {
  return apiRequest<StrategyTypeInfo[]>('/strategy-types', {
    method: 'GET',
    responseContract: 'envelope',
    signal,
    validate: validateStrategyTypes,
  })
}

function validateSingleStrategy(value: unknown): value is StrategyConfig {
  return isStrategyConfig(value)
}

export function changeStrategyMode(variantId: string, mode: StrategyMode): Promise<StrategyConfig> {
  return apiRequest<StrategyConfig>(`/strategy-configs/${encodeURIComponent(variantId)}/mode`, {
    method: 'PUT',
    responseContract: 'envelope',
    body: JSON.stringify({ mode }),
    validate: validateSingleStrategy,
  })
}

/** Creates a new immutable version of the variant (the backend never edits in place). */
export function saveStrategyConfig(request: StrategyConfigRequest): Promise<StrategyConfig> {
  return apiRequest<StrategyConfig>(`/strategy-configs/${encodeURIComponent(request.variantId)}`, {
    method: 'PUT',
    responseContract: 'envelope',
    body: JSON.stringify(request),
    validate: validateSingleStrategy,
  })
}
