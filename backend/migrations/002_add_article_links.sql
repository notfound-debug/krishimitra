-- Add article_links column to digests table
-- Date: 2026-03-19
-- Why: Store source article URLs so the app can show "view original" links on news cards

ALTER TABLE digests ADD COLUMN IF NOT EXISTS article_links JSONB DEFAULT '[]';
