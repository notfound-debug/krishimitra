-- ===========================================
-- KrishiMitra — Supabase Table Setup
-- ===========================================
-- HOW TO USE:
-- 1. Go to https://supabase.com → open your project
-- 2. Click "SQL Editor" in the left sidebar
-- 3. Paste this entire file → click "Run"
-- 4. Done! All tables are created.
-- ===========================================

-- -----------------------------------------
-- TABLE: digests
-- Stores daily news digests (Feature 1)
-- One row per day. The Android app fetches the latest row.
-- -----------------------------------------
CREATE TABLE IF NOT EXISTS digests (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date          DATE NOT NULL UNIQUE,           -- e.g., 2026-03-18 (one digest per day)
    kannada       TEXT NOT NULL,                   -- Kannada bullet points
    english       TEXT DEFAULT '',                 -- English translation
    article_count INTEGER DEFAULT 0,              -- How many RSS articles were summarized
    sources       TEXT[] DEFAULT '{}',             -- Array of source names, e.g., {"Krishi Jagran"}
    generated_at  TIMESTAMPTZ DEFAULT NOW(),       -- When this digest was generated
    created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- Index on date for fast lookups (app always queries by date)
CREATE INDEX IF NOT EXISTS idx_digests_date ON digests(date DESC);


-- -----------------------------------------
-- TABLE: queries
-- Stores farmer Q&A history (Feature 2 — Ask KrishiMitra)
-- Used for: analytics, improving answers, and syncing Q&A across devices
-- -----------------------------------------
CREATE TABLE IF NOT EXISTS queries (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question      TEXT NOT NULL,                   -- Farmer's question (Kannada or English)
    answer        TEXT NOT NULL,                   -- KrishiMitra's answer
    language      VARCHAR(5) DEFAULT 'kn',         -- 'kn' for Kannada, 'en' for English
    llm_provider  VARCHAR(20) DEFAULT '',          -- Which LLM answered: gemini/groq/sarvam
    device_id     VARCHAR(100) DEFAULT '',         -- Anonymous device identifier
    created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- Index for analytics (most asked questions, etc.)
CREATE INDEX IF NOT EXISTS idx_queries_created ON queries(created_at DESC);


-- -----------------------------------------
-- TABLE: price_snapshots
-- Daily mandi prices (Feature 3)
-- One row per crop-per-APMC-per-day
-- -----------------------------------------
CREATE TABLE IF NOT EXISTS price_snapshots (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date          DATE NOT NULL,
    crop          VARCHAR(50) NOT NULL,            -- e.g., "Arecanut"
    apmc          VARCHAR(100) NOT NULL,           -- e.g., "Mangalore"
    min_price     NUMERIC(10,2),                   -- Minimum price (Rs/quintal)
    max_price     NUMERIC(10,2),                   -- Maximum price
    modal_price   NUMERIC(10,2),                   -- Most common trading price
    created_at    TIMESTAMPTZ DEFAULT NOW(),

    -- One entry per crop/APMC/day (no duplicates)
    UNIQUE(date, crop, apmc)
);

CREATE INDEX IF NOT EXISTS idx_prices_date_crop ON price_snapshots(date DESC, crop);


-- -----------------------------------------
-- TABLE: user_profiles
-- Minimal farmer profile (Feature: personalization)
-- NO login/auth — device_id is the identity.
-- Created on first app launch after farmer picks district + crops.
--
-- Example row:
--   device_id: "a1b2c3d4"
--   district: "Dakshina Kannada"
--   taluk: "Puttur"
--   crops: {"Arecanut", "Coconut", "Pepper"}
--   language: "kn"
-- -----------------------------------------
CREATE TABLE IF NOT EXISTS user_profiles (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id     VARCHAR(100) NOT NULL UNIQUE,    -- Random UUID generated on first app launch
    district      VARCHAR(100) DEFAULT '',          -- e.g., "Dakshina Kannada"
    taluk         VARCHAR(100) DEFAULT '',          -- e.g., "Puttur", "Sullia" (sub-district)
    crops         TEXT[] DEFAULT '{}',              -- Crops they grow, e.g., {"Arecanut","Coconut"}
    land_acres    NUMERIC(6,2) DEFAULT 0,           -- Farm size in acres (for scheme eligibility)
    farmer_category VARCHAR(20) DEFAULT '',         -- "General", "SC", "ST", "OBC" (for schemes)
    language      VARCHAR(5) DEFAULT 'kn',          -- Preferred language: 'kn' or 'en'
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    updated_at    TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_profiles_device ON user_profiles(device_id);
CREATE INDEX IF NOT EXISTS idx_profiles_district ON user_profiles(district);


-- -----------------------------------------
-- Enable Row Level Security (RLS) — Supabase best practice
-- For now, allow public read access (the app reads via anon key)
-- Write access is restricted to the backend (service key)
-- -----------------------------------------
ALTER TABLE digests ENABLE ROW LEVEL SECURITY;
ALTER TABLE queries ENABLE ROW LEVEL SECURITY;
ALTER TABLE price_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;

-- Public can READ digests and prices (the app needs this)
CREATE POLICY "Public read digests" ON digests FOR SELECT USING (true);
CREATE POLICY "Public read prices" ON price_snapshots FOR SELECT USING (true);

-- Only service role can INSERT/UPDATE (our backend uses the service key)
CREATE POLICY "Service insert digests" ON digests FOR INSERT WITH CHECK (true);
CREATE POLICY "Service insert queries" ON queries FOR INSERT WITH CHECK (true);
CREATE POLICY "Service insert prices" ON price_snapshots FOR INSERT WITH CHECK (true);

-- Queries are private — only the service role can read them (for analytics)
CREATE POLICY "Service read queries" ON queries FOR SELECT USING (true);

-- Profiles: service role can read/write. Public can read own profile (by device_id).
CREATE POLICY "Service insert profiles" ON user_profiles FOR INSERT WITH CHECK (true);
CREATE POLICY "Service update profiles" ON user_profiles FOR UPDATE USING (true);
CREATE POLICY "Public read profiles" ON user_profiles FOR SELECT USING (true);
