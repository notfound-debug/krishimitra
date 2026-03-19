"""
Ask KrishiMitra Service — Q&A for farmers.

Flow:
1. Farmer sends question + profile (language, district, crops)
2. ChromaDB finds relevant knowledge (diseases, schemes, tips)
3. Today's digest is added for timely context
4. LLM generates personalized answer with source citations
5. Answer saved to Supabase for analytics
"""

import logging
import time

from app.services.llm import generate
from app.services.knowledge import retrieve_context, get_recent_digest_context
from app.services import database as db

logger = logging.getLogger(__name__)

ASK_PROMPT = """You are KrishiMitra (ಕೃಷಿಮಿತ್ರ), a trusted agricultural advisor for farmers in Coastal Karnataka (Dakshina Kannada, Udupi, Uttara Kannada).

FARMER PROFILE:
- District: {district}
- Crops: {crops}
- Language: {language_name}

INSTRUCTIONS:
1. Answer in {language_name}.
2. Be SPECIFIC to coastal Karnataka (tropical, heavy monsoon, laterite soil).
3. Give ACTIONABLE advice — what to do, when, how much, approximate cost.
4. If you mention a pesticide/fertilizer, give the GENERIC name + dosage.
5. If uncertain, say so honestly. Never invent facts.
6. Keep answer concise — 3-8 sentences. Farmers read on small phone screens.
7. If the question is about a government scheme, include the helpline number and portal URL.
8. End with ONE practical next step the farmer can take TODAY.
9. If you use information from the CONTEXT below, mention the source.

{context_block}

FARMER'S QUESTION:
{question}

Answer:"""


async def ask_krishimitra(
    question: str,
    language: str = "kn",
    device_id: str = "",
    district: str = "",
    crops: list[str] | None = None,
) -> dict:
    """
    Answer a farmer's question.

    Returns:
        {
            "question": "original question",
            "answer": "LLM response",
            "sources": ["CPCRI Kasaragod", ...],
            "language": "kn",
        }
    """
    start = time.time()
    crops = crops or []

    # Step 1: Retrieve relevant context from ChromaDB
    kb_results = retrieve_context(question, language, max_results=3)
    digest_context = get_recent_digest_context()

    # Step 2: Build context block for the prompt
    context_parts = []
    sources = []

    if kb_results:
        context_parts.append("RELEVANT KNOWLEDGE FROM OUR DATABASE:")
        for r in kb_results:
            context_parts.append(f"Q: {r['question']}")
            context_parts.append(f"A: {r['answer']}")
            if r.get("source"):
                context_parts.append(f"Source: {r['source']}")
                sources.append(r["source"])
            context_parts.append("")

    if digest_context:
        context_parts.append(digest_context)

    context_block = "\n".join(context_parts) if context_parts else (
        "No specific context found in our database. "
        "Use your general agricultural knowledge for coastal Karnataka."
    )

    language_name = "Kannada (ಕನ್ನಡ)" if language == "kn" else "English"

    # Step 3: Build prompt and call LLM
    prompt = ASK_PROMPT.format(
        district=district or "Coastal Karnataka",
        crops=", ".join(crops) if crops else "General (arecanut, coconut, rice, pepper, cashew)",
        language_name=language_name,
        context_block=context_block,
        question=question,
    )

    answer = await generate(prompt)

    elapsed = time.time() - start
    logger.info(f"Q&A: {elapsed:.1f}s | lang={language} | context={len(kb_results)} entries | sources={sources}")

    # Step 4: Save to Supabase (best-effort, don't block)
    db.save_query(
        question=question,
        answer=answer,
        language=language,
        device_id=device_id,
    )

    return {
        "question": question,
        "answer": answer,
        "sources": list(set(sources)),
        "language": language,
    }
