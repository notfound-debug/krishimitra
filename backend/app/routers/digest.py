"""
Digest Router — HTTP endpoints for the daily news digest.

Endpoints:
  GET  /api/digest          → Returns today's digest (Android app calls this)
  POST /api/digest/generate → Triggers generation (cron job calls this)

The GET endpoint is what farmers actually hit.
It returns a small JSON (~5KB) with Kannada + English summaries.
The app caches this locally, so it only fetches once per day.
"""

from fastapi import APIRouter, HTTPException

from app.services.digest import generate_daily_digest, get_latest_digest

router = APIRouter(prefix="/api/digest", tags=["digest"])


@router.get("")
async def get_digest():
    """
    Get today's news digest.

    Called by the Android app on launch.
    Returns cached digest if available, otherwise 404.

    Response example:
    {
        "date": "2026-03-18",
        "kannada": "💰 ಬೆಲೆ: ಅಡಿಕೆ ₹42,000...",
        "english": "💰 Price: Arecanut ₹42,000...",
        "article_count": 15,
        "sources": ["Krishi Jagran Kannada"],
        "generated_at": "2026-03-18T05:00:00+05:30"
    }
    """
    digest = await get_latest_digest()

    if digest is None:
        raise HTTPException(
            status_code=404,
            detail="Today's digest has not been generated yet. Try again later.",
        )

    return digest


@router.post("/generate")
async def trigger_generate_digest():
    """
    Generate today's digest NOW.

    This is called by:
    1. GitHub Actions cron at 5 AM IST (daily automatic)
    2. Manual trigger during development/testing

    It fetches RSS feeds → summarizes with LLM → saves the digest.
    Takes ~10-20 seconds depending on LLM response time.
    """
    digest = await generate_daily_digest()
    return {
        "status": "success",
        "date": digest["date"],
        "article_count": digest["article_count"],
        "sources": digest["sources"],
    }
