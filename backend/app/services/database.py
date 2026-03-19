"""
Database Service — Supabase client and helper functions.

How Supabase works with our app:
- Supabase gives us a hosted PostgreSQL database
- We talk to it via REST API (not a direct DB connection)
- supabase-py is the official Python client
- Auth uses two keys:
    SUPABASE_URL: Your project URL (e.g., https://abc123.supabase.co)
    SUPABASE_KEY: The "service_role" key (has full write access)
                  NOT the "anon" key (that's for the frontend/app)

Connection flow:
    Our backend → HTTPS → Supabase REST API → PostgreSQL

Why not connect to Postgres directly?
- Supabase's REST layer handles auth, RLS (row-level security), and rate limiting
- Works from HuggingFace Spaces (which blocks raw DB ports)
- The supabase-py client is simpler than raw SQL
"""

import logging
from datetime import datetime, timezone, timedelta

from supabase import create_client, Client

from app.config import settings

logger = logging.getLogger(__name__)

IST = timezone(timedelta(hours=5, minutes=30))

# Supabase client — initialized once, reused everywhere
_client: Client | None = None


def get_client() -> Client | None:
    """
    Get the Supabase client (lazy initialization).

    Returns None if SUPABASE_URL or SUPABASE_KEY aren't configured.
    This lets the app run without Supabase during local development.
    """
    global _client

    if _client is not None:
        return _client

    if not settings.supabase_url or not settings.supabase_key:
        logger.warning(
            "Supabase not configured (SUPABASE_URL or SUPABASE_KEY missing). "
            "Database features disabled — using local file fallback."
        )
        return None

    try:
        _client = create_client(settings.supabase_url, settings.supabase_key)
        logger.info("Supabase client initialized successfully")
        return _client
    except Exception as e:
        logger.error(f"Failed to initialize Supabase client: {e}")
        return None


# -----------------------------------------
# Digest helpers (Feature 1)
# -----------------------------------------

def save_digest(digest: dict) -> bool:
    """
    Save a daily digest to Supabase.

    Uses UPSERT — if a digest for today already exists, it gets updated.
    This is safe to call multiple times (e.g., if we re-generate a digest).

    Args:
        digest: Dict with keys: date, kannada, english, article_count, sources, generated_at

    Returns:
        True if saved successfully, False if Supabase is unavailable.
    """
    client = get_client()
    if client is None:
        return False

    try:
        row = {
            "date": digest["date"],
            "kannada": digest["kannada"],
            "english": digest["english"],
            "article_count": digest["article_count"],
            "sources": digest["sources"],
            "generated_at": digest["generated_at"],
        }

        # Try with article_links first (if column exists in Supabase)
        try:
            row["article_links"] = digest.get("article_links", [])
            client.table("digests").upsert(row, on_conflict="date").execute()
        except Exception:
            # Column might not exist — save without it
            del row["article_links"]
            client.table("digests").upsert(row, on_conflict="date").execute()

        logger.info(f"Digest saved to Supabase for {digest['date']}")
        return True

    except Exception as e:
        logger.error(f"Failed to save digest to Supabase: {e}")
        return False


def get_digest_by_date(date_str: str) -> dict | None:
    """
    Fetch a digest from Supabase by date.

    Args:
        date_str: Date in "YYYY-MM-DD" format (e.g., "2026-03-18")

    Returns:
        Digest dict if found, None otherwise.
    """
    client = get_client()
    if client is None:
        return None

    try:
        result = (
            client.table("digests")
            .select("*")
            .eq("date", date_str)
            .limit(1)
            .execute()
        )

        if result.data:
            row = result.data[0]
            return {
                "date": row["date"],
                "kannada": row["kannada"],
                "english": row.get("english", ""),
                "article_count": row.get("article_count", 0),
                "sources": row.get("sources", []),
                "generated_at": row.get("generated_at", ""),
            }

        return None

    except Exception as e:
        logger.error(f"Failed to fetch digest from Supabase: {e}")
        return None


def get_latest_digest_from_db() -> dict | None:
    """
    Fetch the most recent digest (today's or yesterday's).

    The app calls this when the farmer opens it.
    If today's digest hasn't been generated yet, we return yesterday's
    so the farmer always sees something.
    """
    client = get_client()
    if client is None:
        return None

    try:
        result = (
            client.table("digests")
            .select("*")
            .order("date", desc=True)
            .limit(1)
            .execute()
        )

        if result.data:
            row = result.data[0]
            return {
                "date": row["date"],
                "kannada": row["kannada"],
                "english": row.get("english", ""),
                "article_count": row.get("article_count", 0),
                "sources": row.get("sources", []),
                "generated_at": row.get("generated_at", ""),
            }

        return None

    except Exception as e:
        logger.error(f"Failed to fetch latest digest from Supabase: {e}")
        return None


# -----------------------------------------
# Query helpers (Feature 2 — for later)
# -----------------------------------------

def save_query(question: str, answer: str, language: str = "kn",
               llm_provider: str = "", device_id: str = "") -> bool:
    """Save a Q&A interaction to Supabase for analytics."""
    client = get_client()
    if client is None:
        return False

    try:
        client.table("queries").insert({
            "question": question,
            "answer": answer,
            "language": language,
            "llm_provider": llm_provider,
            "device_id": device_id,
        }).execute()
        return True
    except Exception as e:
        logger.error(f"Failed to save query: {e}")
        return False


# -----------------------------------------
# Price helpers (Feature 3 — for later)
# -----------------------------------------

def save_prices(prices: list[dict]) -> bool:
    """Save a batch of mandi prices to Supabase."""
    client = get_client()
    if client is None:
        return False

    try:
        # upsert: update existing prices for the same date/crop/apmc
        client.table("price_snapshots").upsert(
            prices, on_conflict="date,crop,apmc"
        ).execute()
        logger.info(f"Saved {len(prices)} price records to Supabase")
        return True
    except Exception as e:
        logger.error(f"Failed to save prices: {e}")
        return False


# -----------------------------------------
# User Profile helpers
# -----------------------------------------

def save_profile(profile: dict) -> bool:
    """
    Create or update a farmer's profile.

    Uses upsert on device_id — if the farmer already has a profile,
    it gets updated (e.g., they change their crop selection).

    Args:
        profile: Dict with keys: device_id, district, taluk, crops,
                 land_acres, farmer_category, language
    """
    client = get_client()
    if client is None:
        return False

    try:
        client.table("user_profiles").upsert(
            profile, on_conflict="device_id"
        ).execute()
        logger.info(f"Profile saved for device {profile.get('device_id', '?')}")
        return True
    except Exception as e:
        logger.error(f"Failed to save profile: {e}")
        return False


def get_profile(device_id: str) -> dict | None:
    """
    Fetch a farmer's profile by device_id.

    Returns None if no profile exists (farmer hasn't done onboarding yet).
    """
    client = get_client()
    if client is None:
        return None

    try:
        result = (
            client.table("user_profiles")
            .select("*")
            .eq("device_id", device_id)
            .limit(1)
            .execute()
        )

        if result.data:
            row = result.data[0]
            return {
                "device_id": row["device_id"],
                "district": row.get("district", ""),
                "taluk": row.get("taluk", ""),
                "crops": row.get("crops", []),
                "land_acres": float(row.get("land_acres", 0)),
                "farmer_category": row.get("farmer_category", ""),
                "language": row.get("language", "kn"),
            }

        return None

    except Exception as e:
        logger.error(f"Failed to fetch profile: {e}")
        return None


# ---------------------------------------------------------------------------
# Mandi Prices
# ---------------------------------------------------------------------------

def save_mandi_prices(crop: str, crop_kn: str, prices: list[dict], source: str = "commodityonline") -> bool:
    """
    Save scraped mandi prices to Supabase.
    Uses upsert on (crop, apmc, date) — safe to call multiple times per day.
    """
    client = get_client()
    if client is None:
        return False

    from datetime import date as date_type
    today = str(date_type.today())

    try:
        rows = []
        for p in prices:
            rows.append({
                "crop": crop,
                "crop_kn": crop_kn,
                "apmc": p.get("apmc", ""),
                "min_price": p.get("min_price", 0),
                "max_price": p.get("max_price", 0),
                "modal_price": p.get("modal_price", 0),
                "date": today,
                "source": source,
            })

        if rows:
            client.table("mandi_prices").upsert(
                rows, on_conflict="crop,apmc,date"
            ).execute()
            logger.info(f"Saved {len(rows)} price rows for {crop} ({today})")

        return True
    except Exception as e:
        logger.error(f"Failed to save prices for {crop}: {e}")
        return False


def get_latest_prices(crop: str) -> list[dict] | None:
    """
    Get the most recent prices for a crop from Supabase.
    Tries today first, falls back to most recent date.
    Returns None if no data exists.
    """
    client = get_client()
    if client is None:
        return None

    try:
        result = client.table("mandi_prices") \
            .select("*") \
            .eq("crop", crop) \
            .order("date", desc=True) \
            .limit(20) \
            .execute()

        if result.data:
            return result.data

        return None
    except Exception as e:
        logger.error(f"Failed to fetch prices for {crop}: {e}")
        return None
