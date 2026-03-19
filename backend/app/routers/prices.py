"""
Prices Router — HTTP endpoints for APMC mandi prices.

Endpoints:
  GET  /api/prices/crops       -> List of available crops
  GET  /api/prices/{crop}      -> Prices for a crop across all APMCs

How it works:
1. Delegates all price fetching to app.services.prices
2. The service tries data.gov.in first, then falls back to date-seeded
   realistic mock data that changes daily
3. Identifies the best APMC (highest modal price) and generates
   a Kannada recommendation for the farmer

Data source: https://data.gov.in/resource/current-daily-price-various-commodities-various-centres
"""

import logging

from fastapi import APIRouter, HTTPException

from app.services.prices import fetch_prices, list_available_crops

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/prices", tags=["prices"])


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@router.get("/crops")
async def list_crops():
    """
    List all crops for which price data is available.

    Called by the Android app to populate the crop-picker dropdown.
    Returns crop names in both English and Kannada.
    """
    return {"crops": list_available_crops()}


@router.get("/{crop}")
async def get_crop_prices(crop: str):
    """
    Get today's APMC mandi prices for a specific crop.

    Path parameter `crop` is case-insensitive: "arecanut", "Arecanut",
    "ARECANUT" all work.

    Tries live data.gov.in API first. Falls back to date-seeded realistic
    mock data so the frontend never gets an empty response.
    """
    try:
        result = await fetch_prices(crop)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc))
    except Exception:
        logger.exception("Unexpected error fetching prices for '%s'", crop)
        raise HTTPException(
            status_code=500,
            detail="Failed to fetch price data. Please try again later.",
        )

    logger.info(
        "Returning prices for '%s' — %d APMCs, best: %s",
        crop, len(result["prices"]), result["best_apmc"],
    )
    return result
