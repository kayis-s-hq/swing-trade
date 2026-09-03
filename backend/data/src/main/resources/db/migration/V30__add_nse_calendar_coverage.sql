CREATE TABLE nse_calendar_coverage (
    calendar_year INTEGER PRIMARY KEY,
    status VARCHAR(16) NOT NULL CHECK (status IN ('VERIFIED', 'PENDING')),
    source VARCHAR(255),
    verified_at TIMESTAMP
);

-- These years are covered by the reviewed holiday rows shipped with the application.
INSERT INTO nse_calendar_coverage (calendar_year, status, source, verified_at)
VALUES (2026, 'VERIFIED', 'application holiday calendar', CURRENT_TIMESTAMP),
       (2027, 'VERIFIED', 'application holiday calendar', CURRENT_TIMESTAMP)
ON CONFLICT (calendar_year) DO NOTHING;
