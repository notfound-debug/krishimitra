"""
Price Service — Fetches APMC mandi prices for Karnataka crops.

How it works (waterfall strategy):
1. Try the data.gov.in API (real government open-data endpoint)
2. Try scraping commodityonline.com for mandi prices
3. If both return 0 records, fall back to realistic
   date-seeded mock data that changes every day

The mock fallback uses the current date as a random seed so that:
- Prices look different every day (simulating real market movement)
- Prices are consistent within the same day (same seed = same output)
- Base prices are accurate for 2026 market rates
- APMC-level variance is realistic (Mangalore highest, smaller mandis lower)
- Trend data compares to "yesterday" prices (also seeded)

Covers crops: Arecanut, Coconut, Rice/Paddy, Black Pepper, Cashew
Covers APMCs: Mangalore, Puttur, Sullia, Bantwal, Belthangady, Udupi,
              Kundapura, Karkala
"""

import hashlib
import logging
import random
from datetime import date, timedelta
from typing import Any

import httpx
from bs4 import BeautifulSoup

from app.config import settings

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Crop name mappings
# ---------------------------------------------------------------------------

CROP_NAME_KN: dict[str, str] = {
    "arecanut": "ಅಡಿಕೆ",
    "coconut": "ತೆಂಗಿನಕಾಯಿ",
    "copra": "ಕೊಬ್ಬರಿ",
    "banana": "ಬಾಳೆ",
    "rice": "ಭತ್ತ",
    "paddy": "ಭತ್ತ",
    "black pepper": "ಕಾಳುಮೆಣಸು",
    "cashewnut": "ಗೋಡಂಬಿ",
    "cashew": "ಗೋಡಂಬಿ",
    "cocoa": "ಕೋಕೋ",
    "rubber": "ರಬ್ಬರ್",
}

CROP_DISPLAY_NAME: dict[str, str] = {
    "arecanut": "Arecanut",
    "coconut": "Coconut",
    "copra": "Copra",
    "banana": "Banana",
    "rice": "Rice",
    "paddy": "Rice",
    "black pepper": "Black Pepper",
    "cashewnut": "Cashewnut",
    "cashew": "Cashewnut",
    "cocoa": "Cocoa",
    "rubber": "Rubber",
}

# Commodity names as they appear on data.gov.in
CROP_TO_COMMODITY: dict[str, str] = {
    "arecanut": "Arecanut(Betelnut/Supari)",
    "coconut": "Coconut",
    "copra": "Copra",
    "banana": "Banana",
    "rice": "Paddy(Dhan)(Common)",
    "paddy": "Paddy(Dhan)(Common)",
    "black pepper": "Black Pepper",
    "cashewnut": "Cashewnuts",
    "cashew": "Cashewnuts",
    "cocoa": "Cocoa",
    "rubber": "Rubber",
}

APMC_NAME_KN: dict[str, str] = {
    "Mangalore": "ಮಂಗಳೂರು",
    "Puttur": "ಪುತ್ತೂರು",
    "Sullia": "ಸುಳ್ಯ",
    "Bantwal": "ಬಂಟ್ವಾಳ",
    "Belthangady": "ಬೆಳ್ತಂಗಡಿ",
    "Udupi": "ಉಡುಪಿ",
    "Kundapura": "ಕುಂದಾಪುರ",
    "Karkala": "ಕಾರ್ಕಳ",
    "Sirsi": "ಸಿರ್ಸಿ",
    "Kumta": "ಕುಮಟಾ",
    "Siddapur": "ಸಿದ್ದಾಪುರ",
    "Honnavar": "ಹೊನ್ನಾವರ",
}


# ---------------------------------------------------------------------------
# kisandeals.com scraper config (primary — more reliable than commodityonline)
# ---------------------------------------------------------------------------

KISANDEALS_SLUGS: dict[str, str] = {
    "arecanut": "ARECANUT(BETELNUT-SUPARI)",
    "coconut": "COCONUT",
    "copra": "COPRA",
    "banana": "BANANA",
    "rice": "PADDY(DHAN)(COMMON)",
    "paddy": "PADDY(DHAN)(COMMON)",
    "black pepper": "BLACK-PEPPER",
    "cashewnut": "CASHEWNUTS",
    "cashew": "CASHEWNUTS",
}

# Target APMCs we care about (lowercase for matching)
TARGET_APMC_NAMES = {
    "mangalore", "puttur", "sullia", "bantwal", "belthangdi", "belthangady",
    "udupi", "kundapura", "karkala", "sirsi", "kumta", "siddapur", "honnavar",
    "sagar", "shimoga",
}


# ---------------------------------------------------------------------------
# commodityonline.com scraper config (backup)
# ---------------------------------------------------------------------------

COMMODITY_ONLINE_SLUGS: dict[str, str] = {
    "arecanut": "arecanut-betelnutsupari",
    "coconut": "coconut",
    "copra": "copra",
    "banana": "banana",
    "rice": "paddy-dhan-common",
    "paddy": "paddy-dhan-common",
    "black pepper": "black-pepper",
    "cashewnut": "cashewnuts",
    "cashew": "cashewnuts",
}

COMMODITY_ONLINE_MARKETS: list[str] = [
    "puttur", "mangalore", "sullia", "bantwal", "udupi",
    "sirsi", "kumta", "siddapur", "honnavar",
]


# ---------------------------------------------------------------------------
# Base price configuration for 2026 (per quintal in INR)
# ---------------------------------------------------------------------------
# Each crop defines: base_modal, spread (min/max offset from modal),
# variance_pct (how much smaller mandis differ from the top APMC),
# daily_swing (max daily random movement in absolute rupees),
# and which APMCs typically trade that crop.

CROP_PRICE_CONFIG: dict[str, dict[str, Any]] = {
    "arecanut": {
        "base_modal": 41500,
        "spread": (700, 500),       # (modal - min, max - modal)
        "variance_pct": 0.06,       # smaller mandis up to 6% lower
        "daily_swing": 1200,        # up to +/- ₹1,200 per day
        "apmcs": [
            "Mangalore", "Puttur", "Sullia", "Bantwal",
            "Belthangady", "Udupi", "Kundapura", "Karkala",
        ],
    },
    "coconut": {
        "base_modal": 12800,
        "spread": (800, 800),
        "variance_pct": 0.08,
        "daily_swing": 600,
        "apmcs": [
            "Mangalore", "Puttur", "Bantwal", "Udupi",
            "Kundapura", "Karkala",
        ],
    },
    "rice": {
        "base_modal": 3400,
        "spread": (300, 300),
        "variance_pct": 0.06,
        "daily_swing": 150,
        "apmcs": ["Mangalore", "Puttur", "Udupi", "Kundapura"],
    },
    "black pepper": {
        "base_modal": 62000,
        "spread": (3000, 3000),
        "variance_pct": 0.05,
        "daily_swing": 2500,
        "apmcs": ["Mangalore", "Puttur", "Sullia", "Bantwal"],
    },
    "cashewnut": {
        "base_modal": 18000,
        "spread": (1500, 1500),
        "variance_pct": 0.07,
        "daily_swing": 800,
        "apmcs": ["Mangalore", "Puttur", "Bantwal", "Kundapura"],
    },
    "copra": {
        "base_modal": 10500,
        "spread": (500, 500),
        "variance_pct": 0.06,
        "daily_swing": 400,
        "apmcs": ["Mangalore", "Puttur", "Bantwal", "Udupi", "Kundapura"],
    },
    "banana": {
        "base_modal": 2500,
        "spread": (200, 200),
        "variance_pct": 0.08,
        "daily_swing": 150,
        "apmcs": ["Mangalore", "Puttur", "Bantwal", "Udupi", "Kundapura"],
    },
}

# Alias so "cashew" and "paddy" resolve to the same config
CROP_CONFIG_ALIASES: dict[str, str] = {
    "cashew": "cashewnut",
    "paddy": "rice",
}


# ---------------------------------------------------------------------------
# Deterministic seeded price generation
# ---------------------------------------------------------------------------

def _date_seed(crop_key: str, apmc: str, ref_date: date) -> int:
    """
    Create a deterministic integer seed from crop + APMC + date.

    Uses SHA-256 so small input changes produce very different seeds,
    giving realistic-looking price variation across days and APMCs.
    """
    raw = f"{crop_key}|{apmc}|{ref_date.isoformat()}"
    digest = hashlib.sha256(raw.encode()).hexdigest()
    return int(digest[:12], 16)


def _generate_prices_for_date(
    crop_key: str, ref_date: date
) -> list[dict[str, Any]]:
    """
    Generate realistic APMC price entries for one crop on one date.

    Each APMC gets a slightly different price level. Mangalore (index 0)
    is the baseline; subsequent APMCs are progressively lower by a
    random fraction of variance_pct.
    """
    config_key = CROP_CONFIG_ALIASES.get(crop_key, crop_key)
    config = CROP_PRICE_CONFIG.get(config_key)
    if config is None:
        return []

    base_modal = config["base_modal"]
    spread_low, spread_high = config["spread"]
    variance_pct = config["variance_pct"]
    daily_swing = config["daily_swing"]
    apmcs = config["apmcs"]

    prices = []
    for idx, apmc in enumerate(apmcs):
        rng = random.Random(_date_seed(crop_key, apmc, ref_date))

        # Daily random movement around the base modal
        day_offset = rng.randint(-daily_swing, daily_swing)

        # APMC hierarchy: index 0 (Mangalore) is highest; others drift down
        # The fraction scales with position in the list
        hierarchy_fraction = idx / max(len(apmcs) - 1, 1)
        apmc_discount = int(base_modal * variance_pct * hierarchy_fraction)
        # Add a small random jitter to the discount so it isn't perfectly linear
        apmc_jitter = rng.randint(
            -int(base_modal * 0.005),
            int(base_modal * 0.005),
        )
        apmc_discount = max(0, apmc_discount + apmc_jitter)

        modal_price = base_modal + day_offset - apmc_discount

        # Derive min/max from modal with some jitter on the spread
        min_jitter = rng.randint(-int(spread_low * 0.15), int(spread_low * 0.15))
        max_jitter = rng.randint(-int(spread_high * 0.15), int(spread_high * 0.15))
        min_price = modal_price - spread_low + min_jitter
        max_price = modal_price + spread_high + max_jitter

        # Ensure min < modal < max
        if min_price >= modal_price:
            min_price = modal_price - 100
        if max_price <= modal_price:
            max_price = modal_price + 100

        prices.append({
            "apmc": apmc,
            "min_price": min_price,
            "max_price": max_price,
            "modal_price": modal_price,
        })

    return prices


def _add_trends(
    crop_key: str, today_prices: list[dict[str, Any]], ref_date: date
) -> list[dict[str, Any]]:
    """
    Compare today's prices with yesterday's to compute trend for each APMC.
    """
    yesterday = ref_date - timedelta(days=1)
    yesterday_prices = _generate_prices_for_date(crop_key, yesterday)
    yesterday_map = {p["apmc"]: p["modal_price"] for p in yesterday_prices}

    enriched = []
    for entry in today_prices:
        apmc = entry["apmc"]
        prev_modal = yesterday_map.get(apmc)
        if prev_modal is not None:
            change = entry["modal_price"] - prev_modal
        else:
            change = 0

        if change > 0:
            trend_str = f"+{change}"
            trend_dir = "up"
        elif change < 0:
            trend_str = str(change)
            trend_dir = "down"
        else:
            trend_str = "0"
            trend_dir = "stable"

        enriched.append({
            **entry,
            "trend": trend_str,
            "trend_direction": trend_dir,
        })

    return enriched


# ---------------------------------------------------------------------------
# Recommendation builder
# ---------------------------------------------------------------------------

def _build_recommendation(
    crop_kn: str, best_apmc: str, modal_price: int
) -> tuple[str, str]:
    """
    Generate a selling recommendation in Kannada and English.
    """
    apmc_kn = APMC_NAME_KN.get(best_apmc, best_apmc)

    rec_kn = (
        f"{apmc_kn} ಮಂಡಿಯಲ್ಲಿ {crop_kn} ಅತ್ಯುತ್ತಮ ಬೆಲೆ ₹{modal_price:,}/ಕ್ವಿಂಟಾಲ್. "
        f"ಮಾರಾಟಕ್ಕೆ {apmc_kn} ಮಂಡಿ ಉತ್ತಮ ಆಯ್ಕೆ."
    )
    rec_en = (
        f"Best price for {crop_kn} at {best_apmc} APMC: "
        f"₹{modal_price:,}/quintal. "
        f"Consider selling at {best_apmc} for the best return."
    )
    return rec_kn, rec_en


# ---------------------------------------------------------------------------
# Normalisation helper
# ---------------------------------------------------------------------------

def _normalise_crop(crop: str) -> str:
    """Lowercase and strip the crop name for consistent dict lookups."""
    return crop.strip().lower()


# ---------------------------------------------------------------------------
# data.gov.in live API
# ---------------------------------------------------------------------------

async def _fetch_from_data_gov(crop_key: str) -> dict[str, Any] | None:
    """
    Fetch real-time prices from data.gov.in APMC endpoint.

    Returns a formatted response dict on success, or None if the API
    is unavailable or returns no data for Karnataka.
    """
    api_key = settings.data_gov_in_api_key
    if not api_key:
        logger.info("data.gov.in API key not configured — skipping live fetch")
        return None

    commodity = CROP_TO_COMMODITY.get(crop_key)
    if not commodity:
        logger.warning("No commodity mapping for crop key '%s'", crop_key)
        return None

    url = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070"
    params = {
        "api-key": api_key,
        "format": "json",
        "filters[state]": "Karnataka",
        "filters[commodity]": commodity,
        "limit": 20,
    }

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(url, params=params)
            resp.raise_for_status()
            data = resp.json()
    except httpx.HTTPStatusError as exc:
        logger.error(
            "data.gov.in HTTP %s for '%s': %s",
            exc.response.status_code, commodity, exc.response.text[:200],
        )
        return None
    except httpx.RequestError as exc:
        logger.error("data.gov.in request failed for '%s': %s", commodity, exc)
        return None
    except Exception:
        logger.exception("Unexpected error fetching prices for '%s'", commodity)
        return None

    records = data.get("records", [])
    if not records:
        logger.info("data.gov.in returned 0 records for '%s'", commodity)
        return None

    display_name = CROP_DISPLAY_NAME.get(crop_key, crop_key.title())
    crop_kn = CROP_NAME_KN.get(crop_key, display_name)

    prices: list[dict[str, Any]] = []
    for rec in records:
        try:
            apmc = rec.get("market", "Unknown")
            min_price = int(float(rec.get("min_price", 0)))
            max_price = int(float(rec.get("max_price", 0)))
            modal_price = int(float(rec.get("modal_price", 0)))
        except (ValueError, TypeError):
            logger.warning("Skipping malformed record: %s", rec)
            continue

        prices.append({
            "apmc": apmc,
            "min_price": min_price,
            "max_price": max_price,
            "modal_price": modal_price,
            "trend": "0",
            "trend_direction": "stable",
        })

    if not prices:
        return None

    best = max(prices, key=lambda p: p["modal_price"])
    rec_kn, rec_en = _build_recommendation(crop_kn, best["apmc"], best["modal_price"])

    return {
        "crop": display_name,
        "crop_kn": crop_kn,
        "date": str(date.today()),
        "best_apmc": best["apmc"],
        "prices": prices,
        "recommendation_kn": rec_kn,
        "recommendation_en": rec_en,
    }


# ---------------------------------------------------------------------------
# kisandeals.com scraper (primary)
# ---------------------------------------------------------------------------

async def _try_kisandeals(crop_key: str) -> dict[str, Any] | None:
    """Scrape mandi prices from kisandeals.com for Karnataka."""
    slug = KISANDEALS_SLUGS.get(crop_key)
    if not slug:
        return None

    display_name = CROP_DISPLAY_NAME.get(crop_key, crop_key.title())
    crop_kn = CROP_NAME_KN.get(crop_key, display_name)

    url = f"https://www.kisandeals.com/mandiprices/{slug}/KARNATAKA/ALL"
    try:
        async with httpx.AsyncClient(follow_redirects=True, timeout=15) as client:
            resp = await client.get(url)
            resp.raise_for_status()
    except Exception as e:
        logger.warning(f"kisandeals request failed for {crop_key}: {e}")
        return None

    try:
        from bs4 import BeautifulSoup
        soup = BeautifulSoup(resp.text, "html.parser")
        tables = soup.find_all("table")

        # Find the detailed table (has columns: Commodity, Variety, State, District, Mandi, Min, Modal, Max, Date, Trend)
        price_table = None
        for t in tables:
            headers = [h.text.strip().lower() for h in t.find_all("th")]
            if any("mandi" in h or "market" in h for h in headers):
                price_table = t
                break

        if not price_table:
            # Try the last table (usually the detailed one)
            if len(tables) >= 4:
                price_table = tables[3]
            else:
                return None

        rows = price_table.find_all("tr")[1:]  # Skip header
        prices = []
        seen_apmcs = set()

        for row in rows:
            cells = row.find_all("td")
            if len(cells) < 8:
                continue

            mandi_raw = cells[4].text.strip().lower()

            # Filter to our target APMCs
            matched_apmc = None
            for target in TARGET_APMC_NAMES:
                if target in mandi_raw:
                    matched_apmc = target.title()
                    # Normalize names
                    if matched_apmc == "Belthangdi":
                        matched_apmc = "Belthangady"
                    break

            if not matched_apmc or matched_apmc in seen_apmcs:
                continue
            seen_apmcs.add(matched_apmc)

            # Parse prices — remove "₹" and commas
            def parse_price(text):
                return int(float(text.replace("₹", "").replace(",", "").strip()))

            try:
                min_p = parse_price(cells[5].text)
                modal_p = parse_price(cells[6].text)
                max_p = parse_price(cells[7].text)
            except (ValueError, IndexError):
                continue

            prices.append({
                "apmc": matched_apmc,
                "min_price": min_p,
                "max_price": max_p,
                "modal_price": modal_p,
                "trend": "",
                "trend_direction": "stable",
            })

        if not prices:
            return None

        prices.sort(key=lambda x: x["max_price"], reverse=True)
        best_apmc = prices[0]["apmc"]

        return {
            "crop": display_name,
            "crop_kn": crop_kn,
            "date": str(date.today()),
            "best_apmc": best_apmc,
            "prices": prices,
            "recommendation_kn": f"{APMC_NAME_KN.get(best_apmc, best_apmc)} ಮಂಡಿಯಲ್ಲಿ ಅತ್ಯುತ್ತಮ ಬೆಲೆ.",
            "recommendation_en": f"Best price at {best_apmc} mandi.",
        }

    except Exception as e:
        logger.error(f"kisandeals parse error for {crop_key}: {e}")
        return None


# ---------------------------------------------------------------------------
# commodityonline.com scraper (backup)
# ---------------------------------------------------------------------------

async def _try_commodityonline(crop_key: str) -> dict[str, Any] | None:
    """
    Scrape mandi prices from commodityonline.com as a fallback.

    Tries each market in COMMODITY_ONLINE_MARKETS. Extracts price rows
    from the HTML table. Returns a formatted response or None if scraping
    fails or no data is found.
    """
    slug = COMMODITY_ONLINE_SLUGS.get(crop_key)
    if not slug:
        logger.info("No commodityonline slug for crop '%s'", crop_key)
        return None

    display_name = CROP_DISPLAY_NAME.get(crop_key, crop_key.title())
    crop_kn = CROP_NAME_KN.get(crop_key, display_name)

    prices: list[dict[str, Any]] = []

    async with httpx.AsyncClient(
        timeout=10.0,
        headers={"User-Agent": "Mozilla/5.0 (compatible; KrishiMitra/1.0)"},
    ) as client:
        for market in COMMODITY_ONLINE_MARKETS:
            url = (
                f"https://www.commodityonline.com/mandiprices"
                f"/{slug}/karnataka/{market}"
            )
            try:
                resp = await client.get(url)
                if resp.status_code != 200:
                    logger.debug(
                        "commodityonline %s returned %d", market, resp.status_code
                    )
                    continue

                soup = BeautifulSoup(resp.text, "html.parser")

                # Look for price table rows — the site uses tables with
                # columns: Commodity, Market, Min, Max, Modal, Date
                rows = soup.select("table tr")
                for row in rows:
                    cells = row.find_all("td")
                    if len(cells) < 5:
                        continue

                    try:
                        min_price = int(
                            float(cells[2].get_text(strip=True).replace(",", ""))
                        )
                        max_price = int(
                            float(cells[3].get_text(strip=True).replace(",", ""))
                        )
                        modal_price = int(
                            float(cells[4].get_text(strip=True).replace(",", ""))
                        )
                    except (ValueError, TypeError, IndexError):
                        continue

                    if modal_price <= 0:
                        continue

                    apmc_name = market.capitalize()
                    prices.append({
                        "apmc": apmc_name,
                        "min_price": min_price,
                        "max_price": max_price,
                        "modal_price": modal_price,
                        "trend": "0",
                        "trend_direction": "stable",
                    })
                    break  # One valid row per market is enough

            except httpx.RequestError as exc:
                logger.debug("commodityonline request failed for %s: %s", market, exc)
                continue

    if not prices:
        logger.info("commodityonline returned no prices for '%s'", crop_key)
        return None

    best = max(prices, key=lambda p: p["modal_price"])
    rec_kn, rec_en = _build_recommendation(crop_kn, best["apmc"], best["modal_price"])

    logger.info(
        "commodityonline returned %d market(s) for '%s'", len(prices), crop_key
    )
    return {
        "crop": display_name,
        "crop_kn": crop_kn,
        "date": str(date.today()),
        "best_apmc": best["apmc"],
        "prices": prices,
        "recommendation_kn": rec_kn,
        "recommendation_en": rec_en,
    }


# ---------------------------------------------------------------------------
# Date-seeded realistic fallback
# ---------------------------------------------------------------------------

def _generate_fallback_response(crop_key: str) -> dict[str, Any] | None:
    """
    Generate a full price response using date-seeded random data.

    Prices change every day but are consistent within the same day.
    Returns None only if the crop has no price configuration.
    """
    config_key = CROP_CONFIG_ALIASES.get(crop_key, crop_key)
    if config_key not in CROP_PRICE_CONFIG:
        return None

    today = date.today()
    display_name = CROP_DISPLAY_NAME.get(crop_key, crop_key.title())
    crop_kn = CROP_NAME_KN.get(crop_key, display_name)

    today_prices = _generate_prices_for_date(crop_key, today)
    if not today_prices:
        return None

    prices = _add_trends(crop_key, today_prices, today)

    best = max(prices, key=lambda p: p["modal_price"])
    rec_kn, rec_en = _build_recommendation(crop_kn, best["apmc"], best["modal_price"])

    return {
        "crop": display_name,
        "crop_kn": crop_kn,
        "date": str(today),
        "best_apmc": best["apmc"],
        "prices": prices,
        "recommendation_kn": rec_kn,
        "recommendation_en": rec_en,
    }


# ---------------------------------------------------------------------------
# Cron: scrape all crops and save to DB
# ---------------------------------------------------------------------------

ALL_CROPS_TO_SCRAPE = ["arecanut", "coconut", "copra", "rice", "black pepper", "cashew", "banana"]

async def scrape_and_save_all_crops() -> dict:
    """
    Scrape prices for all crops and save to Supabase.
    Called by the cron job (5x/day) after digest generation.
    """
    from app.services import database as db
    results = {}

    for crop_key in ALL_CROPS_TO_SCRAPE:
        display_name = CROP_DISPLAY_NAME.get(crop_key, crop_key.title())
        crop_kn = CROP_NAME_KN.get(crop_key, display_name)

        try:
            # Try kisandeals first (most reliable)
            scraped = await _try_kisandeals(crop_key)
            if scraped and scraped.get("prices"):
                db.save_mandi_prices(display_name, crop_kn, scraped["prices"], "kisandeals")
                results[crop_key] = f"OK ({len(scraped['prices'])} markets via kisandeals)"
                continue

            # Try commodityonline
            scraped = await _try_commodityonline(crop_key)
            if scraped and scraped.get("prices"):
                db.save_mandi_prices(display_name, crop_kn, scraped["prices"], "commodityonline")
                results[crop_key] = f"OK ({len(scraped['prices'])} markets via commodityonline)"
                continue

            # Try data.gov.in
            live = await _fetch_from_data_gov(crop_key)
            if live and live.get("prices"):
                db.save_mandi_prices(display_name, crop_kn, live["prices"], "data.gov.in")
                results[crop_key] = f"OK ({len(live['prices'])} markets via data.gov)"
                continue

            results[crop_key] = "SKIP (no data from any source)"

        except Exception as e:
            results[crop_key] = f"FAIL ({e})"
            logger.error(f"Price scrape failed for {crop_key}: {e}")

    logger.info(f"Price scrape results: {results}")
    return results


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

async def fetch_prices(crop: str) -> dict[str, Any]:
    """
    Fetch APMC mandi prices for a given crop.

    Strategy:
    1. Try data.gov.in live API
    2. Try commodityonline.com scraper
    3. Fall back to date-seeded realistic mock data

    Args:
        crop: Crop name (case-insensitive). E.g. "Arecanut", "rice".

    Returns:
        Dict with crop info, per-APMC prices, trends, and recommendations.

    Raises:
        ValueError: If the crop is not recognised.
    """
    crop_key = _normalise_crop(crop)

    if crop_key not in CROP_DISPLAY_NAME:
        available = sorted({v for v in CROP_DISPLAY_NAME.values()})
        raise ValueError(
            f"Crop '{crop}' not found. Available: {', '.join(available)}"
        )

    display_name = CROP_DISPLAY_NAME.get(crop_key, crop_key.title())
    crop_kn = CROP_NAME_KN.get(crop_key, display_name)

    # --- Strategy 1: Supabase DB (fastest, most reliable) ---
    from app.services import database as db
    db_rows = db.get_latest_prices(display_name)
    if db_rows:
        # Group by date — use the most recent date's data
        latest_date = db_rows[0]["date"]
        today_rows = [r for r in db_rows if r["date"] == latest_date]
        if today_rows:
            prices = []
            for r in today_rows:
                prices.append({
                    "apmc": r["apmc"],
                    "min_price": r["min_price"],
                    "max_price": r["max_price"],
                    "modal_price": r["modal_price"],
                    "trend": "",
                    "trend_direction": "stable",
                })
            # Sort by max_price descending
            prices.sort(key=lambda x: x["max_price"], reverse=True)
            best_apmc = prices[0]["apmc"] if prices else ""
            result = {
                "crop": display_name,
                "crop_kn": crop_kn,
                "date": str(latest_date),
                "best_apmc": best_apmc,
                "prices": prices,
                "recommendation_kn": f"{APMC_NAME_KN.get(best_apmc, best_apmc)} ಮಂಡಿಯಲ್ಲಿ ಅತ್ಯುತ್ತಮ ಬೆಲೆ.",
                "recommendation_en": f"Best price at {best_apmc} mandi.",
                "price_source": "database",
            }
            logger.info(f"Returning DB prices for '{crop_key}': {len(prices)} APMCs, date={latest_date}")
            return result

    # --- Strategy 2: Scrape kisandeals.com (primary scraper) + save to DB ---
    try:
        scraped = await _try_kisandeals(crop_key)
        if scraped is not None:
            scraped["price_source"] = "kisandeals"
            db.save_mandi_prices(display_name, crop_kn, scraped["prices"], source="kisandeals")
            logger.info(f"kisandeals: saved prices for '{crop_key}': {len(scraped['prices'])} markets")
            return scraped
    except Exception:
        logger.exception("kisandeals scrape failed for '%s'", crop_key)

    # --- Strategy 3: Scrape commodityonline (backup scraper) ---
    try:
        scraped = await _try_commodityonline(crop_key)
        if scraped is not None:
            scraped["price_source"] = "commodityonline"
            db.save_mandi_prices(display_name, crop_kn, scraped["prices"], source="commodityonline")
            return scraped
    except Exception:
        logger.exception("commodityonline scrape failed for '%s'", crop_key)

    # --- Strategy 4: data.gov.in API ---
    try:
        live = await _fetch_from_data_gov(crop_key)
        if live is not None:
            live["price_source"] = "data.gov.in"
            db.save_mandi_prices(display_name, crop_kn, live["prices"], source="data.gov.in")
            return live
    except Exception:
        logger.exception("data.gov.in failed for '%s'", crop_key)

    # --- Strategy 4: Return most recent DB data (any date) ---
    if db_rows:
        # We have old data — return it with the actual date
        latest_date = db_rows[0]["date"]
        prices = [{"apmc": r["apmc"], "min_price": r["min_price"], "max_price": r["max_price"],
                    "modal_price": r["modal_price"], "trend": "", "trend_direction": "stable"}
                   for r in db_rows if r["date"] == latest_date]
        prices.sort(key=lambda x: x["max_price"], reverse=True)
        return {
            "crop": display_name, "crop_kn": crop_kn, "date": str(latest_date),
            "best_apmc": prices[0]["apmc"] if prices else "",
            "prices": prices,
            "recommendation_kn": "ಹಳೆಯ ಬೆಲೆ — ಇಂದಿನ ಬೆಲೆ ಶೀಘ್ರದಲ್ಲಿ ಲಭ್ಯ.",
            "recommendation_en": "Older prices — today's prices available soon.",
            "price_source": "database_old",
        }

    # --- No data at all ---
    logger.error(f"No price data available for '{crop_key}'")
    return {
        "crop": display_name, "crop_kn": crop_kn,
        "date": str(date.today()), "best_apmc": "", "prices": [],
        "recommendation_kn": "ಬೆಲೆ ಲಭ್ಯವಿಲ್ಲ — ಶೀಘ್ರದಲ್ಲಿ ಪ್ರಯತ್ನಿಸಿ.",
        "recommendation_en": "Prices unavailable — try again soon.",
        "price_source": "none",
    }

    # Should never reach here
    logger.error("No price data available at all for '%s'", crop_key)
    display_name = CROP_DISPLAY_NAME.get(crop_key, crop_key.title())
    crop_kn = CROP_NAME_KN.get(crop_key, display_name)
    return {
        "crop": display_name,
        "crop_kn": crop_kn,
        "date": str(date.today()),
        "best_apmc": "N/A",
        "prices": [],
        "recommendation_kn": "ಬೆಲೆ ಮಾಹಿತಿ ಲಭ್ಯವಿಲ್ಲ.",
        "recommendation_en": "Price data is currently unavailable.",
    }


def list_available_crops() -> list[dict[str, str]]:
    """
    Return a deduplicated list of available crops with English and Kannada names.

    Used by the /api/prices/crops endpoint.
    """
    crops: list[dict[str, str]] = []
    seen: set[str] = set()
    for key, display in CROP_DISPLAY_NAME.items():
        if display in seen:
            continue
        seen.add(display)
        crops.append({
            "name": display,
            "name_kn": CROP_NAME_KN.get(key, display),
            "key": key,
        })
    return crops
