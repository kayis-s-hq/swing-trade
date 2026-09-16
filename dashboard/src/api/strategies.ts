import { apiRequest } from './shared'
import type { StrategyConfig, StrategyMode } from './types'

const MODES = new Set<StrategyMode>(['OFF', 'BACKTEST_ONLY', 'SHADOW', 'CHAMPION'])

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
