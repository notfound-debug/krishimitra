"""
Central configuration — loads all API keys from .env file.

How it works:
- Pydantic Settings reads .env automatically
- Every key defaults to "" (empty string) so the app won't crash
  if you haven't set a key yet
- Other files import `settings` from here to access keys
"""

from pydantic_settings import BaseSettings
from pathlib import Path


class Settings(BaseSettings):
    # --- LLMs (fallback order: Gemini → Groq → Sarvam) ---
    gemini_api_key: str = ""
    groq_api_key: str = ""
    sarvam_api_key: str = ""

    # --- Supabase ---
    supabase_url: str = ""
    supabase_key: str = ""

    # --- Data APIs ---
    data_gov_in_api_key: str = ""
    openweather_api_key: str = ""

    # --- HuggingFace ---
    hf_token: str = ""

    # --- App Settings ---
    # RSS feeds for daily news digest
    rss_feeds: list[str] = [
        "https://krishijagran.com/feeds/rss",              # English agri news (60+ articles)
        "https://eng.ruralvoice.in/rss/latest-posts",      # Indian agri policy, schemes
        "https://eng.ruralvoice.in/rss/category/state",    # State-level agri news
        "https://kannada.krishijagran.com/rss/",            # Kannada agri news
        "https://www.prajavani.net/feed",                   # Kannada general KA news
        "https://www.vijayavani.net/api/rss",               # Kannada general KA news
        "https://www.deccanherald.com/feed",                # English KA news
        "https://www.agrifarming.in/feed",                  # Agri how-to guides
    ]

    # Target region APMCs
    target_apmcs: list[str] = [
        "Mangalore", "Puttur", "Sullia", "Bantwal", "Belthangady",
        "Udupi", "Kundapura", "Karkala", "Brahmavar",
    ]

    # Target crops
    target_crops: list[str] = [
        "Arecanut", "Coconut", "Rice", "Black Pepper",
        "Cashew", "Cocoa", "Rubber",
    ]

    model_config = {
        "env_file": str(Path(__file__).resolve().parent.parent / ".env"),
        "env_file_encoding": "utf-8",
    }


# Single instance used across the app
settings = Settings()
