"""
LLM Service — Fallback chain: Gemini → Groq → Sarvam

How it works:
1. Caller passes a prompt (e.g., "Summarize these articles in Kannada")
2. We try Gemini first (best Kannada support, 1000 req/day free)
3. If Gemini fails (quota/error), we try Groq (runs Llama 3, 14K req/day free)
4. If Groq fails, we try Sarvam (Indian language specialist)
5. If ALL fail, we return a polite Kannada error message

Each provider has a different API format:
- Gemini: POST to generativelanguage.googleapis.com
- Groq: POST to api.groq.com (OpenAI-compatible format)
- Sarvam: POST to api.sarvam.ai
"""

import httpx
import logging

from app.config import settings

logger = logging.getLogger(__name__)

# Timeout for LLM calls (seconds) — rural internet can be slow
LLM_TIMEOUT = 30.0

# Kannada error message shown to farmers if all LLMs fail
FALLBACK_ERROR = (
    "ಕ್ಷಮಿಸಿ, ಈ ಸಮಯದಲ್ಲಿ ಉತ್ತರಿಸಲು ಸಾಧ್ಯವಾಗಲಿಲ್ಲ. "
    "ದಯವಿಟ್ಟು ಸ್ವಲ್ಪ ಸಮಯದ ನಂತರ ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ."
    # Translation: "Sorry, unable to answer at this time. Please try again later."
)


async def _call_gemini(prompt: str) -> str | None:
    """
    Google Gemini Flash-Lite API.

    Endpoint: generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent
    Auth: API key as query parameter
    Free tier: ~1,000 requests/day
    """
    if not settings.gemini_api_key:
        logger.debug("Gemini: No API key configured, skipping")
        return None

    url = (
        "https://generativelanguage.googleapis.com/v1beta/"
        "models/gemini-2.0-flash-lite:generateContent"
    )
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.3,  # Low temperature = more factual, less creative
            "maxOutputTokens": 2048,
        },
    }

    try:
        async with httpx.AsyncClient(timeout=LLM_TIMEOUT) as client:
            resp = await client.post(
                url,
                params={"key": settings.gemini_api_key},
                json=payload,
            )
            resp.raise_for_status()
            data = resp.json()
            # Gemini response structure: candidates[0].content.parts[0].text
            return data["candidates"][0]["content"]["parts"][0]["text"]
    except httpx.HTTPStatusError as e:
        logger.warning(f"Gemini HTTP error {e.response.status_code}: {e.response.text[:200]}")
        return None
    except Exception as e:
        logger.warning(f"Gemini error: {e}")
        return None


async def _call_groq(prompt: str) -> str | None:
    """
    Groq API — runs Llama 3 70B at high speed.

    Endpoint: api.groq.com/openai/v1/chat/completions
    Auth: Bearer token
    Free tier: 30 req/min, ~14,400 req/day
    Format: OpenAI-compatible (so easy to use)
    """
    if not settings.groq_api_key:
        logger.debug("Groq: No API key configured, skipping")
        return None

    url = "https://api.groq.com/openai/v1/chat/completions"
    payload = {
        "model": "llama-3.3-70b-versatile",
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0.3,
        "max_tokens": 2048,
    }

    try:
        async with httpx.AsyncClient(timeout=LLM_TIMEOUT) as client:
            resp = await client.post(
                url,
                headers={"Authorization": f"Bearer {settings.groq_api_key}"},
                json=payload,
            )
            resp.raise_for_status()
            data = resp.json()
            # OpenAI format: choices[0].message.content
            return data["choices"][0]["message"]["content"]
    except httpx.HTTPStatusError as e:
        logger.warning(f"Groq HTTP error {e.response.status_code}: {e.response.text[:200]}")
        return None
    except Exception as e:
        logger.warning(f"Groq error: {e}")
        return None


async def _call_sarvam(prompt: str) -> str | None:
    """
    Sarvam AI — purpose-built for Indian languages.

    Endpoint: api.sarvam.ai/v1/chat/completions
    Auth: Bearer token
    Free credits: Rs 1000 on signup
    Best for: Kannada translation and understanding
    """
    if not settings.sarvam_api_key:
        logger.debug("Sarvam: No API key configured, skipping")
        return None

    url = "https://api.sarvam.ai/v1/chat/completions"
    payload = {
        "model": "sarvam-m",
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0.3,
        "max_tokens": 2048,
    }

    try:
        async with httpx.AsyncClient(timeout=LLM_TIMEOUT) as client:
            resp = await client.post(
                url,
                headers={
                    "Authorization": f"Bearer {settings.sarvam_api_key}",
                    "Content-Type": "application/json",
                },
                json=payload,
            )
            resp.raise_for_status()
            data = resp.json()
            return data["choices"][0]["message"]["content"]
    except httpx.HTTPStatusError as e:
        logger.warning(f"Sarvam HTTP error {e.response.status_code}: {e.response.text[:200]}")
        return None
    except Exception as e:
        logger.warning(f"Sarvam error: {e}")
        return None


# --- The main function other files call ---

async def generate(prompt: str) -> str:
    """
    Send a prompt to the LLM fallback chain.

    Tries: Gemini → Groq → Sarvam → error message.
    Returns the generated text (never raises an exception).

    Usage:
        from app.services.llm import generate
        answer = await generate("Summarize these articles in Kannada: ...")
    """
    # Try each provider in order
    for name, call_fn in [
        ("Gemini", _call_gemini),
        ("Groq", _call_groq),
        ("Sarvam", _call_sarvam),
    ]:
        result = await call_fn(prompt)
        if result:
            logger.info(f"LLM response from: {name}")
            return result
        # If we reach here, this provider failed — try next

    # All providers failed
    logger.error("All LLM providers failed!")
    return FALLBACK_ERROR
