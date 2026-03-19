"""
Ask Router — Q&A endpoint for farmers.

Endpoint:
  POST /api/ask → Submit a question, get an AI-powered answer

The Android app calls this when the farmer types a question
in the chat screen and taps send.
"""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.ask import ask_krishimitra
from app.services import database as db

router = APIRouter(prefix="/api/ask", tags=["ask"])


class AskRequest(BaseModel):
    question: str                    # Farmer's question text
    language: str = "kn"             # "kn" or "en"
    device_id: str = ""              # For profile lookup + analytics
    district: str = ""               # From cached profile (optional)
    crops: list[str] = []            # From cached profile (optional)


class AskResponse(BaseModel):
    question: str
    answer: str
    sources: list[str] = []
    language: str = "kn"


@router.post("", response_model=AskResponse)
async def ask_question(req: AskRequest):
    """
    Answer a farmer's agricultural question.

    Uses ChromaDB to find relevant knowledge, then LLM generates
    a personalized answer. Response time: typically 3-10 seconds.
    """
    # If device_id provided but no district/crops, fetch from profile
    district = req.district
    crops = req.crops

    if req.device_id and (not district or not crops):
        profile = db.get_profile(req.device_id)
        if profile:
            district = district or profile.get("district", "")
            crops = crops or profile.get("crops", [])

    result = await ask_krishimitra(
        question=req.question,
        language=req.language,
        device_id=req.device_id,
        district=district,
        crops=crops,
    )

    return AskResponse(**result)
