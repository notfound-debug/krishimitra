"""
Insight Router — Smart rule-based farming insights.

Endpoint:
  GET /api/insight?crop=arecanut&language=kn

How it works (NO LLM — pure data analysis):
1. Fetch today's prices for the crop (reuses the price service waterfall)
2. Look at trend_direction across APMCs to determine overall trend
3. Generate a simple rule-based actionable insight in Kannada + English
4. Return insight with crop name, emoji, and trend direction

This is used by the Home screen's "💡 ಸಲಹೆ" stat card.
"""

import logging
from fastapi import APIRouter, HTTPException, Query

from app.services.prices import fetch_prices, CROP_NAME_KN, CROP_DISPLAY_NAME

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/insight", tags=["insight"])

# Emoji for each crop (used in insight display)
CROP_EMOJI: dict[str, str] = {
    "arecanut": "🌴",
    "coconut": "🥥",
    "rice": "🌾",
    "paddy": "🌾",
    "black pepper": "🌶️",
    "cashewnut": "🥜",
    "cashew": "🥜",
    "cocoa": "🍫",
    "rubber": "🌳",
    "copra": "🥥",
    "banana": "🍌",
}


def _determine_trend(prices: list[dict]) -> tuple[str, int]:
    """
    Determine overall trend from per-APMC price data.

    Returns (trend_direction, avg_change) where:
    - trend_direction: "up", "down", or "stable"
    - avg_change: average price change across APMCs (signed int)
    """
    if not prices:
        return "stable", 0

    changes = []
    for p in prices:
        trend_str = p.get("trend", "0")
        try:
            changes.append(int(trend_str.replace("+", "")))
        except (ValueError, TypeError):
            changes.append(0)

    if not changes:
        return "stable", 0

    avg = sum(changes) // len(changes)

    # Threshold: small movements are "stable"
    up_count = sum(1 for c in changes if c > 0)
    down_count = sum(1 for c in changes if c < 0)

    if up_count > down_count and avg > 0:
        return "up", avg
    elif down_count > up_count and avg < 0:
        return "down", avg
    else:
        return "stable", avg


def _generate_insight(
    crop_key: str, crop_kn: str, emoji: str,
    trend: str, avg_change: int, best_apmc: str, best_price: int,
) -> tuple[str, str]:
    """
    Generate rule-based insight in Kannada and English.

    Rules:
    - Price rising → "wait 1-2 days, may go higher"
    - Price falling → "sell now before it drops more"
    - Price stable → "normal market, sell at your convenience"
    """
    if trend == "up" and avg_change > 0:
        kn = (
            f"{emoji} {crop_kn}: ಬೆಲೆ ↑ ₹{avg_change:,} ಹೆಚ್ಚಳ. "
            f"ಇನ್ನೂ 1-2 ದಿನ ಕಾಯಿರಿ, ಬೆಲೆ ↑ ಸಾಧ್ಯ."
        )
        en = (
            f"{emoji} {crop_key.title()}: Price up by ~₹{avg_change:,}. "
            f"Consider waiting 1-2 days — may rise further."
        )
    elif trend == "down" and avg_change < 0:
        drop = abs(avg_change)
        kn = (
            f"{emoji} {crop_kn}: ಬೆಲೆ ↓ ₹{drop:,} ಕುಸಿತ. "
            f"ಈಗ ಮಾರಿ, ಬೆಲೆ ↓ ಆಗಬಹುದು."
        )
        en = (
            f"{emoji} {crop_key.title()}: Price down by ~₹{drop:,}. "
            f"Consider selling now before further drop."
        )
    else:
        kn = (
            f"{emoji} {crop_kn}: ಬೆಲೆ ಸ್ಥಿರ. "
            f"ಸಾಮಾನ್ಯ ಸಮಯದಲ್ಲಿ ಮಾರಿ."
        )
        en = (
            f"{emoji} {crop_key.title()}: Price stable. "
            f"Sell at your convenience."
        )

    return kn, en


@router.get("")
async def get_insight(
    crop: str = Query(..., description="Crop name (e.g., arecanut, coconut, rice)"),
    language: str = Query("kn", description="Language: kn (Kannada) or en (English)"),
):
    """
    Get a smart, rule-based farming insight for a crop.

    Analyzes today's price trends across APMCs and returns actionable
    advice — no LLM needed.
    """
    crop_key = crop.strip().lower()

    if crop_key not in CROP_DISPLAY_NAME:
        raise HTTPException(
            status_code=404,
            detail=f"Crop '{crop}' not found. Try: arecanut, coconut, rice, black pepper, cashew",
        )

    try:
        price_data = await fetch_prices(crop_key)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc))
    except Exception:
        logger.exception("Failed to fetch prices for insight on '%s'", crop)
        raise HTTPException(status_code=500, detail="Failed to generate insight.")

    prices = price_data.get("prices", [])
    best_apmc = price_data.get("best_apmc", "N/A")

    # Find best modal price
    best_price = 0
    if prices:
        best_price = max(p.get("modal_price", 0) for p in prices)

    # Determine trend from price data
    trend, avg_change = _determine_trend(prices)

    # Get crop metadata
    display_name = CROP_DISPLAY_NAME.get(crop_key, crop_key.title())
    crop_kn = CROP_NAME_KN.get(crop_key, display_name)
    emoji = CROP_EMOJI.get(crop_key, "🌱")

    # Generate rule-based insight
    insight_kn, insight_en = _generate_insight(
        crop_key, crop_kn, emoji, trend, avg_change, best_apmc, best_price,
    )

    return {
        "crop": display_name,
        "crop_kn": crop_kn,
        "insight_kn": insight_kn,
        "insight_en": insight_en,
        "trend_direction": trend,
        "avg_change": avg_change,
        "best_apmc": best_apmc,
        "best_price": best_price,
        "date": price_data.get("date", ""),
    }
