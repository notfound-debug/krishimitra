-- KrishiMitra Database Schema
-- Run these in Supabase SQL Editor if setting up a new project
-- Date: 2026-03-19

-- Digests table — stores daily news digests
CREATE TABLE IF NOT EXISTS digests (
    date TEXT PRIMARY KEY,
    kannada TEXT NOT NULL DEFAULT '',
    english TEXT NOT NULL DEFAULT '',
    article_count INTEGER DEFAULT 0,
    sources JSONB DEFAULT '[]',
    article_links JSONB DEFAULT '[]',
    generated_at TIMESTAMPTZ DEFAULT NOW()
);

-- User profiles table — farmer onboarding data
CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR UNIQUE NOT NULL,
    district VARCHAR DEFAULT '',
    taluk VARCHAR DEFAULT '',
    crops TEXT[] DEFAULT '{}',
    land_acres NUMERIC DEFAULT 0,
    farmer_category VARCHAR DEFAULT '',
    language VARCHAR DEFAULT 'kn',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Queries table — Q&A chat history for analytics
CREATE TABLE IF NOT EXISTS queries (
    id BIGSERIAL PRIMARY KEY,
    question TEXT NOT NULL,
    answer TEXT NOT NULL DEFAULT '',
    language VARCHAR DEFAULT 'kn',
    llm_provider VARCHAR DEFAULT '',
    device_id VARCHAR DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_profiles_device ON user_profiles(device_id);
CREATE INDEX IF NOT EXISTS idx_queries_device ON queries(device_id);
CREATE INDEX IF NOT EXISTS idx_queries_created ON queries(created_at);
