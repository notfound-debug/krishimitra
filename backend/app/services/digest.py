"""
Digest Service — Generates the daily Kannada news digest.

Flow (keyword-first, minimal LLM):
1. Fetch latest articles from RSS feeds (news.py)
2. Categorize each article with keyword matching — NO LLM (news.py)
3. Score & pick top 7-10 most relevant articles — NO LLM
4. Format structured items with emoji + category + title + summary — NO LLM
5. ONE small LLM call: generate action lines from just the titles (~500 tokens)
6. Combine articles + action lines → save as digest

This cuts LLM usage by ~90% compared to sending all articles to the LLM.

The daily digest is the CORE feature of the app.
Farmers open the app in the morning → see today's digest → done in 2 minutes.
"""

import json
import logging
from datetime import datetime, timezone, timedelta
from pathlib import Path

from app.services.llm import generate
from app.services.news import fetch_articles, categorize_article, Article
from app.services import database as db

logger = logging.getLogger(__name__)

# Where we save digest JSON files locally (backup + offline dev)
DIGEST_DIR = Path(__file__).resolve().parent.parent / "data" / "digests"
DIGEST_DIR.mkdir(parents=True, exist_ok=True)

# IST timezone (UTC+5:30) — digests are generated for Indian farmers
IST = timezone(timedelta(hours=5, minutes=30))

# --- Category metadata ---
# Maps category keys to emoji + Kannada label + English label
CATEGORY_META = {
    "price":   {"emoji": "💰", "kn": "ಬೆಲೆ",      "en": "Price"},
    "crop":    {"emoji": "🌿", "kn": "ಬೆಳೆ",      "en": "Crop"},
    "scheme":  {"emoji": "🏛️", "kn": "ಯೋಜನೆ",    "en": "Scheme"},
    "weather": {"emoji": "🌧️", "kn": "ಹವಾಮಾನ",   "en": "Weather"},
    "impact":  {"emoji": "⚠️", "kn": "ಪ್ರಭಾವ",    "en": "Impact"},
    "general": {"emoji": "📰", "kn": "ಸಾಮಾನ್ಯ",   "en": "General"},
}

# Relevance score per category — agri-specific categories rank higher
CATEGORY_SCORE = {
    "price": 5, "crop": 5, "scheme": 4, "weather": 4, "impact": 3, "general": 1,
}

# Target crop keywords — articles mentioning these get a boost
TARGET_CROP_KEYWORDS = [
    "arecanut", "betelnut", "ಅಡಿಕೆ", "coconut", "ತೆಂಗು", "rice", "paddy", "ಭತ್ತ",
    "pepper", "ಮೆಣಸು", "cashew", "ಗೋಡಂಬಿ", "cocoa", "ಕೋಕೋ", "rubber", "ರಬ್ಬರ್",
]


def _score_article(article: Article, category: str) -> int:
    """Score an article for relevance. Higher = more relevant to our farmers."""
    score = CATEGORY_SCORE.get(category, 1)
    text = (article.title + " " + article.summary).lower()
    # Boost if it mentions target crops
    if any(crop in text for crop in TARGET_CROP_KEYWORDS):
        score += 3
    # Boost if it mentions Karnataka/coastal districts
    if any(place in text for place in ["karnataka", "ಕರ್ನಾಟಕ", "mangalore", "ಮಂಗಳೂರು", "udupi", "ಉಡುಪಿ", "puttur", "sullia"]):
        score += 2
    return score


def _select_top_articles(articles: list[Article], max_items: int = 10) -> list[tuple[Article, str]]:
    """
    Categorize all articles with keywords and pick the top ones by relevance.
    Returns list of (article, category) tuples, sorted by score descending.
    """
    scored = []
    for article in articles:
        category = categorize_article(article.title, article.summary)
        score = _score_article(article, category)
        scored.append((article, category, score))

    # Sort by score descending, then by date
    scored.sort(key=lambda x: x[2], reverse=True)

    # Take top N, but try to have at most 3 per category for variety
    selected: list[tuple[Article, str]] = []
    category_counts: dict[str, int] = {}
    for article, category, _score in scored:
        if len(selected) >= max_items:
            break
        count = category_counts.get(category, 0)
        if count >= 3:
            continue  # Skip — enough from this category
        selected.append((article, category))
        category_counts[category] = count + 1

    # If we haven't filled up, add remaining regardless of category limit
    if len(selected) < max_items:
        already = {id(a) for a, _ in selected}
        for article, category, _score in scored:
            if len(selected) >= max_items:
                break
            if id(article) not in already:
                selected.append((article, category))

    return selected


# --- Lean LLM prompt: only titles → action lines ---
ACTION_LINE_PROMPT = """You are KrishiMitra, an agri assistant for farmers in Coastal Karnataka.

Below are {count} news headlines relevant to farmers. For EACH headline, write ONE short action line in Kannada (simple spoken Kannada) telling the farmer what to DO or what it MEANS for them.

RULES:
- One action line per headline, in the SAME order
- Each line MUST start with →
- Keep each line under 80 characters
- Be practical and specific

HEADLINES:
{headlines}

ALSO write the same action lines in English after a separator line: ---ENGLISH---

Action lines:"""


async def generate_daily_digest() -> dict:
    """
    Generate today's news digest using keyword categorization + minimal LLM.

    New flow (90% less LLM usage):
    1. Fetch articles from RSS (free)
    2. Categorize each with keywords (free)
    3. Score & pick top 7-10 by relevance (free)
    4. Format structured items with emoji + category + title + summary (free)
    5. ONE small LLM call: generate action lines from just the titles (~500 tokens)
    6. Combine articles + action lines → save as digest
    """
    today = datetime.now(IST).strftime("%Y-%m-%d")
    logger.info(f"Generating digest for {today}")

    # Step 1: Fetch articles from RSS (FREE)
    articles = await fetch_articles(max_per_feed=10)

    if not articles:
        logger.warning("No articles fetched — returning empty digest")
        return {
            "date": today,
            "kannada": "ಇಂದು ಹೊಸ ಕೃಷಿ ಸುದ್ದಿಗಳು ಲಭ್ಯವಿಲ್ಲ.",
            "english": "No new agricultural news available today.",
            "article_count": 0,
            "sources": [],
            "generated_at": datetime.now(IST).isoformat(),
        }

    # Step 2+3: Categorize with keywords + pick top articles (FREE)
    top_articles = _select_top_articles(articles, max_items=10)
    logger.info(f"Selected {len(top_articles)} articles from {len(articles)} total")

    # Step 4: Build headlines for the LLM (just titles — not full articles)
    headlines = "\n".join(
        f"{i}. {article.title}" for i, (article, _cat) in enumerate(top_articles, 1)
    )

    # Step 5: ONE small LLM call — action lines only (~500 tokens total)
    prompt = ACTION_LINE_PROMPT.format(count=len(top_articles), headlines=headlines)
    raw_action_lines = await generate(prompt)

    # Step 6: Parse action lines and combine with article data
    kn_actions, en_actions = _parse_action_lines(raw_action_lines, len(top_articles))

    # Build Kannada digest
    kannada_lines = []
    english_lines = []
    for idx, (article, category) in enumerate(top_articles):
        meta = CATEGORY_META.get(category, CATEGORY_META["general"])
        # Kannada line: emoji + Kannada category label + title (which may already be Kannada)
        kn_line = f"* {meta['emoji']} {meta['kn']}: {article.title}"
        if article.summary:
            # Add first ~150 chars of summary for context
            short_summary = article.summary[:150].rstrip()
            if len(article.summary) > 150:
                short_summary += "..."
            kn_line += f"\n  {short_summary}"
        kn_line += f"\n{kn_actions[idx]}"
        kannada_lines.append(kn_line)

        # English line
        en_line = f"* {meta['emoji']} {meta['en']}: {article.title}"
        en_line += f"\n{en_actions[idx]}"
        english_lines.append(en_line)

    kannada = "\n\n".join(kannada_lines)
    english = "\n\n".join(english_lines)

    # Build digest object
    sources = list(set(a.source for a, _ in top_articles))
    article_links = [
        {"title": a.title, "link": a.link, "source": a.source}
        for a, _ in top_articles if a.link
    ]
    digest = {
        "date": today,
        "kannada": kannada,
        "english": english,
        "article_count": len(articles),
        "sources": sources,
        "article_links": article_links,
        "generated_at": datetime.now(IST).isoformat(),
    }

    # Step 7: Save to Supabase (primary) + local JSON (backup)
    saved_to_db = db.save_digest(digest)
    _save_local(digest, today)

    if saved_to_db:
        logger.info("Digest saved to Supabase ✓")
    else:
        logger.warning("Supabase save failed — local JSON backup was saved")

    logger.info(
        f"Digest generated: {len(top_articles)} items from {len(articles)} articles, "
        f"{len(kannada)} chars Kannada, {len(english)} chars English"
    )
    return digest


def _parse_action_lines(raw: str, expected_count: int) -> tuple[list[str], list[str]]:
    """
    Parse the LLM's action line response into Kannada and English lists.

    The LLM returns something like:
    → ಮಂಗಳೂರು ಮಂಡಿಯಲ್ಲಿ ಮಾರಾಟ ಲಾಭಕರ
    → ಈ ವಾರ ಬೋರ್ಡೋ ಸಿಂಪಡಿಸಿ
    ---ENGLISH---
    → Selling in Mangalore mandi is profitable
    → Spray Bordeaux mixture this week
    """
    import re

    # Split Kannada and English parts
    kn_raw = raw
    en_raw = ""
    for pattern in [r"---+\s*ENGLISH\s*---+", r"ENGLISH\s*:?\s*\n"]:
        parts = re.split(pattern, raw, maxsplit=1, flags=re.IGNORECASE)
        if len(parts) == 2:
            kn_raw = parts[0]
            en_raw = parts[1]
            break

    # Extract lines starting with →
    kn_lines = [line.strip() for line in kn_raw.split("\n") if line.strip().startswith("→")]
    en_lines = [line.strip() for line in en_raw.split("\n") if line.strip().startswith("→")]

    # Pad with generic fallbacks if LLM gave fewer lines than expected
    while len(kn_lines) < expected_count:
        kn_lines.append("→ ವಿವರಗಳಿಗೆ ಲಿಂಕ್ ನೋಡಿ")  # "See link for details"
    while len(en_lines) < expected_count:
        en_lines.append("→ See article for details")

    return kn_lines[:expected_count], en_lines[:expected_count]


def _split_digest(raw: str) -> tuple[str, str]:
    """
    Split the LLM response into Kannada and English parts.

    LLMs are wildly inconsistent. We've seen:
    - ---ENGLISH---
    - ---\nENGLISH
    - Just "ENGLISH" on its own line
    - "ENGLISH VERSION:"
    """
    import re
    # Try patterns from most specific to least specific
    patterns = [
        r"---+\s*ENGLISH\s*---+",         # ---ENGLISH---
        r"---+\s*ENGLISH\s*",              # ---ENGLISH
        r"ENGLISH\s*VERSION\s*:?",         # ENGLISH VERSION:
        r"ENGLISH\s*TRANSLATION\s*:?",     # ENGLISH TRANSLATION:
        r"\n\s*ENGLISH\s*\n",              # standalone "ENGLISH" on its own line
    ]
    for pattern in patterns:
        match = re.split(pattern, raw, maxsplit=1, flags=re.IGNORECASE)
        if len(match) == 2:
            kannada = match[0].strip()
            english = match[1].strip()
            # Sanity check: Kannada part should have Kannada characters
            if kannada and english:
                return kannada, english

    # No separator found — return all as Kannada
    return raw.strip(), ""


def _save_local(digest: dict, date_str: str) -> None:
    """Save digest as a local JSON file (backup + useful for offline dev)."""
    filepath = DIGEST_DIR / f"digest_{date_str}.json"
    try:
        filepath.write_text(json.dumps(digest, ensure_ascii=False, indent=2), encoding="utf-8")
        logger.info(f"Saved local digest: {filepath}")
    except Exception as e:
        logger.warning(f"Failed to save local digest: {e}")


async def get_latest_digest() -> dict | None:
    """
    Get the latest digest. Lookup order:

    1. Supabase (most recent digest — could be today or yesterday)
    2. Local JSON file for today (fallback if Supabase is down)
    3. None (no digest available)

    This is called by the /api/digest endpoint.
    Why check Supabase first?
    - On HuggingFace Spaces, local files are ephemeral (deleted on restart)
    - Supabase is the persistent source of truth
    - Local file is just a backup for when Supabase is unreachable
    """
    # Try Supabase first (primary storage)
    digest = db.get_latest_digest_from_db()
    if digest:
        return digest

    # Fallback: check local JSON file for today
    today = datetime.now(IST).strftime("%Y-%m-%d")
    filepath = DIGEST_DIR / f"digest_{today}.json"

    if filepath.exists():
        try:
            logger.info("Supabase unavailable — serving from local file")
            return json.loads(filepath.read_text(encoding="utf-8"))
        except Exception as e:
            logger.warning(f"Failed to read local digest: {e}")

    return None
