-- V26: Add broker_position_id column to positions table
-- Required by PositionEntity.brokerPositionId field (broker-enriched position tracking)

ALTER TABLE positions ADD COLUMN IF NOT EXISTS broker_position_id VARCHAR(64);
