"""
KrishiMitra Backend — FastAPI Application Entry Point.

This is the file that starts the whole backend.

To run locally:
    cd backend
    pip install -r requirements.txt
    uvicorn app.main:app --reload

What happens on startup:
1. FastAPI app is created
2. GZip middleware is added (compresses responses for slow internet)
3. CORS middleware is added (allows Android app to call our API)
4. All routers are registered (digest, ask, prices, etc.)
5. Server starts listening on port 8000

Endpoints available after startup:
    GET  /              → Health check ("KrishiMitra is running")
    GET  /api/digest    → Today's news digest
    POST /api/digest/generate → Trigger digest generation
    GET  /docs          → Auto-generated API docs (Swagger UI)
"""

import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware

from app.routers import ask as ask_router
from app.routers import digest as digest_router
from app.routers import prices as prices_router
from app.routers import profile as profile_router
from app.routers import schemes as schemes_router
from app.routers import weather as weather_router
from app.routers import insight as insight_router

# Set up logging so we can see what's happening
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(name)s | %(levelname)s | %(message)s",
)

# --- Create the FastAPI app ---
app = FastAPI(
    title="KrishiMitra API",
    description="AI Agricultural Assistant for Karnataka Farmers",
    version="0.1.0",
)

# --- Middleware ---

# GZip: Compresses all responses > 500 bytes.
# A 5KB digest JSON becomes ~1.5KB over the wire.
# Critical for farmers on slow 2G/3G connections.
app.add_middleware(GZipMiddleware, minimum_size=500)

# CORS: Allows the Android app (and browser during dev) to call our API.
# In production, you'd restrict origins. For dev, we allow all.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- Register Routers ---
# Each router handles a group of endpoints (digest, ask, prices, etc.)
# We'll add more routers as we build more features.
app.include_router(ask_router.router)
app.include_router(digest_router.router)
app.include_router(prices_router.router)
app.include_router(profile_router.router)
app.include_router(schemes_router.router)
app.include_router(weather_router.router)
app.include_router(insight_router.router)


# --- Health Check ---
@app.get("/")
async def health_check():
    """
    Simple health check endpoint.
    If you hit http://localhost:8000/ and see this response, the server is running.
    """
    return {
        "app": "KrishiMitra",
        "status": "running",
        "version": "0.1.0",
        "message": "ಕೃಷಿಮಿತ್ರ ಸಿದ್ಧವಾಗಿದೆ!",  # "KrishiMitra is ready!"
    }
