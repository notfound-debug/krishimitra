-- Create mandi_prices table for storing real scraped prices
-- Date: 2026-03-19
-- Why: Store scraped prices in DB so app always has real data, not mock
-- Run this in Supabase SQL Editor before deploying

CREATE TABLE IF NOT EXISTS mandi_prices (
    id BIGSERIAL PRIMARY KEY,
    crop VARCHAR NOT NULL,
    crop_kn VARCHAR NOT NULL,
    apmc VARCHAR NOT NULL,
    min_price INTEGER,
    max_price INTEGER,
    modal_price INTEGER,
    date DATE NOT NULL,
    source VARCHAR DEFAULT 'commodityonline',
    fetched_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(crop, apmc, date)
);

CREATE INDEX IF NOT EXISTS idx_prices_crop_date ON mandi_prices(crop, date DESC);
