"""
Profile Router — HTTP endpoints for farmer profiles.

Endpoints:
  GET  /api/profile/{device_id}  → Get a farmer's profile
  POST /api/profile              → Create or update a profile

How onboarding works in the app:
1. Farmer installs app → app generates a random device_id (UUID)
2. First launch shows 3 quick questions:
   - "ನಿಮ್ಮ ಜಿಲ್ಲೆ?" (Your district?) → dropdown: DK, Udupi, etc.
   - "ನೀವು ಯಾವ ಬೆಳೆ ಬೆಳೆಯುತ್ತೀರಿ?" (What crops?) → multi-select
   - "ಭಾಷೆ?" (Language?) → Kannada / English
3. App sends POST /api/profile with their choices
4. From then on, we personalize: show arecanut prices if they grow arecanut,
   show Puttur APMC if they're in Puttur taluk, etc.
5. No login, no OTP, no friction. Device = identity.
"""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.services import database as db

router = APIRouter(prefix="/api/profile", tags=["profile"])


class ProfileCreate(BaseModel):
    """
    Profile data sent from the Android app.

    All fields except device_id are optional — farmer can skip
    anything they don't want to answer.
    """
    device_id: str                          # Required: random UUID from app
    district: str = ""                      # e.g., "Dakshina Kannada"
    taluk: str = ""                         # e.g., "Puttur"
    crops: list[str] = []                   # e.g., ["Arecanut", "Coconut"]
    land_acres: float = 0                   # Farm size
    farmer_category: str = ""               # "General", "SC", "ST", "OBC"
    language: str = "kn"                    # "kn" or "en"


@router.post("")
async def create_or_update_profile(profile: ProfileCreate):
    """
    Create or update a farmer's profile.

    If a profile with this device_id already exists, it gets updated.
    Called after onboarding and whenever the farmer changes settings.
    """
    saved = db.save_profile(profile.model_dump())

    if not saved:
        raise HTTPException(
            status_code=503,
            detail="Database unavailable. Profile saved locally on your device.",
        )

    return {"status": "success", "device_id": profile.device_id}


@router.get("/{device_id}")
async def get_profile(device_id: str):
    """
    Get a farmer's profile by device_id.

    Called by the app on launch to load saved preferences.
    Returns 404 if no profile exists (farmer hasn't done onboarding).
    """
    profile = db.get_profile(device_id)

    if profile is None:
        raise HTTPException(
            status_code=404,
            detail="Profile not found. Please complete onboarding.",
        )

    return profile
