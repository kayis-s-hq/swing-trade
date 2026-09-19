-- Align the seeded 2026 equity calendar with NSE's published calendar.
-- Unknown dates remain trading days by default; only explicitly listed dates are closed.
DELETE FROM nse_holidays
WHERE holiday_date IN (
    '2026-03-17', '2026-03-30', '2026-04-02', '2026-04-06', '2026-04-09',
    '2026-06-05', '2026-06-20', '2026-07-15', '2026-08-14', '2026-08-17',
    '2026-10-21', '2026-11-05'
);

INSERT INTO nse_holidays (holiday_date, occasion, holiday_type) VALUES
    ('2026-03-03', 'Holi', 'FULL'),
    ('2026-03-26', 'Shri Ram Navami', 'FULL'),
    ('2026-03-31', 'Shri Mahavir Jayanti', 'FULL'),
    ('2026-04-03', 'Good Friday', 'FULL'),
    ('2026-04-14', 'Dr. Baba Saheb Ambedkar Jayanti', 'FULL'),
    ('2026-05-28', 'Bakri Id', 'FULL'),
    ('2026-06-26', 'Muharram', 'FULL'),
    ('2026-09-14', 'Ganesh Chaturthi', 'FULL'),
    ('2026-11-10', 'Diwali-Balipratipada', 'FULL'),
    ('2026-11-24', 'Prakash Gurpurb Sri Guru Nanak Dev', 'FULL'),
    ('2026-11-08', 'Diwali Laxmi Pujan / Muhurat Trading', 'PARTIAL')
ON CONFLICT (holiday_date) DO UPDATE SET
    occasion = EXCLUDED.occasion,
    holiday_type = EXCLUDED.holiday_type;
