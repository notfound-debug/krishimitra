"""
Schemes Router — Government scheme discovery for Karnataka farmers.

Endpoints:
    GET /api/schemes           → All available schemes
    GET /api/schemes/eligible  → Schemes filtered by farmer profile

The scheme data lives in app/data/schemes.json and is loaded once at
module import time (it's static reference data, not user data).
"""

import json
import logging
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, Query

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/schemes", tags=["schemes"])

# ---------------------------------------------------------------------------
# Load scheme data once at startup
# ---------------------------------------------------------------------------
_SCHEMES_FILE = Path(__file__).resolve().parent.parent / "data" / "schemes.json"

try:
    with open(_SCHEMES_FILE, "r", encoding="utf-8") as f:
        _ALL_SCHEMES: list[dict] = json.load(f)
    logger.info("Loaded %d schemes from %s", len(_ALL_SCHEMES), _SCHEMES_FILE)
except FileNotFoundError:
    logger.error("schemes.json not found at %s — returning empty list", _SCHEMES_FILE)
    _ALL_SCHEMES = []
except json.JSONDecodeError as exc:
    logger.error("Invalid JSON in schemes.json: %s", exc)
    _ALL_SCHEMES = []


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _is_eligible(scheme: dict, *, district: str | None, crops: list[str], land_acres: float | None, category: str | None) -> bool:
    """
    Return True if the farmer matches this scheme's eligibility rules.

    Convention in schemes.json:
        - max_land_acres == 0   → no land-size restriction
        - districts == []       → all districts eligible
        - crops == []           → all crops eligible
        - categories == []      → all categories eligible
    """
    elig = scheme.get("eligibility", {})

    # Land size check
    max_land = elig.get("max_land_acres", 0)
    if max_land > 0 and land_acres is not None and land_acres > max_land:
        return False

    # District check
    allowed_districts = elig.get("districts", [])
    if allowed_districts and district:
        if district.lower() not in [d.lower() for d in allowed_districts]:
            return False

    # Crop check — farmer is eligible if *any* of their crops appear in the
    # scheme's crop list (or if the scheme has no crop restriction).
    allowed_crops = elig.get("crops", [])
    if allowed_crops and crops:
        farmer_crops_lower = {c.lower() for c in crops}
        scheme_crops_lower = {c.lower() for c in allowed_crops}
        if not farmer_crops_lower & scheme_crops_lower:
            return False

    # Category check (small, marginal, SC, ST, general, etc.)
    allowed_categories = elig.get("categories", [])
    if allowed_categories and category:
        if category.lower() not in [c.lower() for c in allowed_categories]:
            return False

    return True


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@router.get("")
async def list_schemes():
    """Return every scheme we know about."""
    return {"count": len(_ALL_SCHEMES), "schemes": _ALL_SCHEMES}


@router.get("/eligible")
async def eligible_schemes(
    district: Optional[str] = Query(None, description="Farmer's district, e.g. 'Mandya'"),
    crops: Optional[str] = Query(None, description="Comma-separated crop names, e.g. 'rice,ragi'"),
    land_acres: Optional[float] = Query(None, description="Total cultivable land in acres"),
    category: Optional[str] = Query(None, description="Farmer category: small, marginal, SC, ST, general, etc."),
):
    """
    Return only the schemes a farmer qualifies for, based on the provided
    query parameters.  Parameters that are omitted are treated as
    'don't filter on this criterion'.
    """
    crop_list = [c.strip() for c in crops.split(",") if c.strip()] if crops else []

    matched = [
        s for s in _ALL_SCHEMES
        if _is_eligible(s, district=district, crops=crop_list, land_acres=land_acres, category=category)
    ]

    return {"count": len(matched), "schemes": matched}
