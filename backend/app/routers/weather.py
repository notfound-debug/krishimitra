"""
Weather Router — Current weather for Karnataka districts.

Endpoint:
  GET /api/weather?district=Dakshina Kannada

Uses OpenWeatherMap API (free tier: 1000 calls/day).
Maps district names to lat/lon coordinates, fetches current weather,
and returns structured data with Kannada descriptions.
"""

import logging

import httpx
from fastapi import APIRouter, HTTPException, Query

from app.config import settings

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/weather", tags=["weather"])

# District → lat/lon mapping for Coastal Karnataka
DISTRICT_COORDS: dict[str, tuple[float, float]] = {
    "dakshina kannada": (12.8698, 74.8426),   # Mangalore
    "udupi": (13.3409, 74.7421),
    "uttara kannada": (14.7937, 74.1297),      # Karwar
}

# Kannada weather descriptions for common conditions
WEATHER_DESC_KN: dict[str, str] = {
    "clear sky": "ನಿರಭ್ರ ಆಕಾಶ",
    "few clouds": "ಸ್ವಲ್ಪ ಮೋಡ",
    "scattered clouds": "ಚದುರಿದ ಮೋಡ",
    "broken clouds": "ಮೋಡ ಕವಿದಿದೆ",
    "overcast clouds": "ಮೋಡ ಕವಿದಿದೆ",
    "shower rain": "ಮಳೆ ಸುರಿಯುತ್ತಿದೆ",
    "rain": "ಮಳೆ",
    "light rain": "ಹಗುರ ಮಳೆ",
    "moderate rain": "ಮಧ್ಯಮ ಮಳೆ",
    "heavy intensity rain": "ಭಾರೀ ಮಳೆ",
    "thunderstorm": "ಗುಡುಗು ಮಳೆ",
    "mist": "ಮಂಜು",
    "haze": "ಮಂಜು",
    "fog": "ದಟ್ಟ ಮಂಜು",
    "drizzle": "ತುಂತುರು ಮಳೆ",
    "light drizzle": "ತುಂತುರು ಮಳೆ",
}


@router.get("")
async def get_weather(district: str = Query(default="Dakshina Kannada")):
    """
    Get current weather for a Karnataka district.

    Returns temperature, humidity, wind, description (in English and Kannada),
    and whether rain is expected.
    """
    district_key = district.strip().lower()
    coords = DISTRICT_COORDS.get(district_key)
    if not coords:
        raise HTTPException(
            status_code=404,
            detail=f"District '{district}' not found. "
            f"Available: Dakshina Kannada, Udupi, Uttara Kannada",
        )

    api_key = settings.openweather_api_key
    if not api_key:
        raise HTTPException(
            status_code=503,
            detail="Weather service not configured.",
        )

    lat, lon = coords
    url = "https://api.openweathermap.org/data/2.5/weather"
    params = {
        "lat": lat,
        "lon": lon,
        "appid": api_key,
        "units": "metric",
    }

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(url, params=params)
            resp.raise_for_status()
            data = resp.json()
    except httpx.HTTPStatusError as exc:
        logger.error(
            "OpenWeatherMap HTTP %s: %s",
            exc.response.status_code, exc.response.text[:200],
        )
        raise HTTPException(status_code=502, detail="Weather API error.")
    except httpx.RequestError as exc:
        logger.error("OpenWeatherMap request failed: %s", exc)
        raise HTTPException(status_code=502, detail="Weather service unreachable.")

    # Extract fields
    main = data.get("main", {})
    wind = data.get("wind", {})
    weather_list = data.get("weather", [{}])
    weather_info = weather_list[0] if weather_list else {}
    rain = data.get("rain", {})

    description_en = weather_info.get("description", "")
    description_kn = WEATHER_DESC_KN.get(description_en, description_en)

    # Determine rain forecast
    rain_mm = rain.get("1h", 0)
    has_rain = rain_mm > 0 or "rain" in description_en.lower() or "drizzle" in description_en.lower()

    return {
        "district": district,
        "temp": round(main.get("temp", 0), 1),
        "feels_like": round(main.get("feels_like", 0), 1),
        "humidity": main.get("humidity", 0),
        "wind_speed": round(wind.get("speed", 0), 1),
        "description_en": description_en,
        "description_kn": description_kn,
        "rain_forecast": has_rain,
        "rain_mm": rain_mm,
    }
