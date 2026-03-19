-- Add variety column to mandi_prices
-- Date: 2026-03-19
-- Why: Same crop at same mandi can have different varieties (Rashi, Bette etc.)
--      Each variety has different prices — we need to store them separately

ALTER TABLE mandi_prices ADD COLUMN IF NOT EXISTS variety VARCHAR DEFAULT '';
ALTER TABLE mandi_prices DROP CONSTRAINT IF EXISTS mandi_prices_crop_apmc_date_key;
ALTER TABLE mandi_prices ADD CONSTRAINT mandi_prices_crop_apmc_variety_date_key UNIQUE(crop, apmc, variety, date);
