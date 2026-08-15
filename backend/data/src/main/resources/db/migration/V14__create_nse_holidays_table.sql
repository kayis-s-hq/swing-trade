-- NSE market holidays
CREATE TABLE IF NOT EXISTS nse_holidays (
    id            BIGSERIAL PRIMARY KEY,
    holiday_date  DATE NOT NULL,
    occasion      VARCHAR(128) NOT NULL,
    holiday_type  VARCHAR(32) NOT NULL DEFAULT 'FULL' CHECK (holiday_type IN ('FULL', 'PARTIAL')),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_nse_holiday_date UNIQUE (holiday_date)
);

DROP INDEX IF EXISTS idx_nse_holidays_date;
CREATE INDEX idx_nse_holidays_date ON nse_holidays(holiday_date);

-- FY2026-27 holidays (April 2026 - March 2027)
-- Source: NSE official holiday calendar (verify against nseindia.com)
INSERT INTO nse_holidays (holiday_date, occasion, holiday_type) VALUES
-- 2026
('2026-01-26', 'Republic Day', 'FULL'),
('2026-03-17', 'Holi', 'FULL'),
('2026-03-30', 'Good Friday', 'FULL'),
('2026-04-02', 'Mahavir Jayanti', 'FULL'),
('2026-04-09', 'Eid ul-Adha', 'FULL'),
('2026-04-06', 'Eid ul-Fitr', 'FULL'),
('2026-05-01', 'Maharashtra Day', 'FULL'),
('2026-06-05', 'Shri Krishna Janmashtami', 'FULL'),
('2026-06-20', 'Muharram', 'FULL'),
('2026-07-15', 'Ganesh Chaturthi', 'FULL'),
('2026-08-14', 'Veer Savarkar Jayanti', 'PARTIAL'),
('2026-08-17', 'Id-e-Milad', 'FULL'),
('2026-10-02', 'Gandhi Jayanti', 'FULL'),
('2026-10-20', 'Diwali (Lakshmi Pujan)', 'FULL'),
('2026-10-21', 'Diwali Padva / Fair Monday', 'PARTIAL'),
('2026-11-05', 'Guru Nanak Jayanti', 'FULL'),
('2026-12-25', 'Christmas', 'FULL'),
-- 2027
('2027-01-26', 'Republic Day', 'FULL'),
('2027-03-29', 'Good Friday', 'FULL'),
('2027-03-22', 'Holi', 'FULL'),
('2027-01-01', 'New Year', 'FULL'),
('2027-02-26', 'Muharram', 'FULL'),
('2027-03-12', 'Maha Shivaratri', 'FULL'),
('2027-03-20', 'Eid ul-Fitr', 'FULL')
ON CONFLICT (holiday_date) DO UPDATE SET
    occasion = EXCLUDED.occasion,
    holiday_type = EXCLUDED.holiday_type;