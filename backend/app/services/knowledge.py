"""
Knowledge Base Service — ChromaDB semantic search for farmer Q&A.

How it works:
1. On startup: loads agri_faq.json + schemes.json into ChromaDB
2. ChromaDB converts each entry into a vector (list of numbers representing meaning)
3. When farmer asks a question: convert question to vector → find closest matches
4. Return matched entries as context for the LLM prompt

Why ChromaDB over keyword matching:
- Farmer says "ಅಡಿಕೆ ಕಪ್ಪಾಗುತ್ತಿದೆ" (arecanut turning black)
- Keyword match: FAILS (word "ಕೊಳೆ" not in question)
- Semantic search: SUCCEEDS (meaning of "turning black" is close to "fruit rot disease")

The embedding model (default in ChromaDB) handles Kannada and English.
"""

import json
import logging
from pathlib import Path

import chromadb

logger = logging.getLogger(__name__)

# Paths
_FAQ_FILE = Path(__file__).resolve().parent.parent / "data" / "knowledge_base" / "agri_faq.json"
_SCHEMES_FILE = Path(__file__).resolve().parent.parent / "data" / "schemes.json"

# ChromaDB client — lazy initialized on first query to save startup RAM
_client = None
_collection = None
_initialized = False


def _init_knowledge_base():
    """Load FAQ + schemes into ChromaDB. Called lazily on first query."""
    global _client, _collection, _initialized

    if _initialized:
        return

    _initialized = True

    try:
        _client = chromadb.Client()
        _collection = _client.get_or_create_collection(
            name="krishi_knowledge",
            metadata={"hnsw:space": "cosine"},  # cosine similarity for text
        )

        # Check if already populated
        if _collection.count() > 0:
            logger.info(f"Knowledge base already loaded: {_collection.count()} entries")
            return

        documents = []
        metadatas = []
        ids = []

        # Load FAQ entries
        if _FAQ_FILE.exists():
            faqs = json.loads(_FAQ_FILE.read_text(encoding="utf-8"))
            for faq in faqs:
                # Index both English and Kannada versions for better matching
                # English version
                doc_en = f"{faq.get('question_en', '')} {faq.get('answer_en', '')}"
                documents.append(doc_en)
                metadatas.append({
                    "id": faq["id"],
                    "type": "faq",
                    "lang": "en",
                    "source": faq.get("source", ""),
                    "topic": faq.get("topic", ""),
                })
                ids.append(f"faq-en-{faq['id']}")

                # Kannada version
                doc_kn = f"{faq.get('question_kn', '')} {faq.get('answer_kn', '')}"
                documents.append(doc_kn)
                metadatas.append({
                    "id": faq["id"],
                    "type": "faq",
                    "lang": "kn",
                    "source": faq.get("source", ""),
                    "topic": faq.get("topic", ""),
                })
                ids.append(f"faq-kn-{faq['id']}")

            logger.info(f"Loaded {len(faqs)} FAQ entries ({len(faqs) * 2} documents with both languages)")

        # Load schemes
        if _SCHEMES_FILE.exists():
            schemes = json.loads(_SCHEMES_FILE.read_text(encoding="utf-8"))
            for scheme in schemes:
                doc = (
                    f"{scheme.get('name_en', '')} {scheme.get('name_kn', '')} "
                    f"{scheme.get('description_en', '')} {scheme.get('description_kn', '')} "
                    f"{scheme.get('benefit_en', '')} {scheme.get('benefit_kn', '')}"
                )
                documents.append(doc)
                metadatas.append({
                    "id": scheme["id"],
                    "type": "scheme",
                    "lang": "both",
                    "source": scheme.get("apply_url", ""),
                    "topic": "scheme",
                })
                ids.append(f"scheme-{scheme['id']}")

            logger.info(f"Loaded {len(schemes)} scheme entries")

        # Add all to ChromaDB
        if documents:
            _collection.add(
                documents=documents,
                metadatas=metadatas,
                ids=ids,
            )
            logger.info(f"Knowledge base indexed: {_collection.count()} total documents in ChromaDB")

    except Exception as e:
        logger.error(f"Failed to initialize knowledge base: {e}")


# Lazy init — called on first query, not on module load
# This prevents OOM on HF Spaces during startup


def retrieve_context(question: str, language: str = "kn", max_results: int = 3) -> list[dict]:
    """
    Find relevant knowledge for a farmer's question using semantic search.

    Args:
        question: The farmer's question (Kannada or English)
        language: Preferred language for results ("kn" or "en")
        max_results: Maximum number of context entries to return

    Returns:
        List of dicts with: question, answer, source, type
    """
    # Lazy init — first query triggers ChromaDB setup
    _init_knowledge_base()

    if _collection is None or _collection.count() == 0:
        logger.warning("Knowledge base is empty — no context available")
        return []

    try:
        # Query ChromaDB — it finds semantically similar documents
        results = _collection.query(
            query_texts=[question],
            n_results=max_results * 2,  # Fetch more, then filter by language
        )

        if not results or not results["documents"] or not results["documents"][0]:
            return []

        # Load raw FAQ data for getting the full answer in correct language
        faq_lookup = {}
        if _FAQ_FILE.exists():
            faqs = json.loads(_FAQ_FILE.read_text(encoding="utf-8"))
            faq_lookup = {f["id"]: f for f in faqs}

        scheme_lookup = {}
        if _SCHEMES_FILE.exists():
            schemes = json.loads(_SCHEMES_FILE.read_text(encoding="utf-8"))
            scheme_lookup = {s["id"]: s for s in schemes}

        context_entries = []
        seen_ids = set()

        for i, metadata in enumerate(results["metadatas"][0]):
            entry_id = metadata.get("id", "")

            # Skip duplicates (we indexed both EN and KN for each FAQ)
            if entry_id in seen_ids:
                continue
            seen_ids.add(entry_id)

            entry_type = metadata.get("type", "")
            source = metadata.get("source", "")

            if entry_type == "faq" and entry_id in faq_lookup:
                faq = faq_lookup[entry_id]
                lang_key = "_kn" if language == "kn" else "_en"
                context_entries.append({
                    "question": faq.get(f"question{lang_key}", ""),
                    "answer": faq.get(f"answer{lang_key}", ""),
                    "source": faq.get("source", ""),
                    "type": "faq",
                })

            elif entry_type == "scheme" and entry_id in scheme_lookup:
                scheme = scheme_lookup[entry_id]
                lang_key = "_kn" if language == "kn" else "_en"
                steps = scheme.get(f"steps{lang_key}", [])
                steps_text = " ".join(f"{i+1}. {s}" for i, s in enumerate(steps))
                context_entries.append({
                    "question": f"How to apply for {scheme.get(f'name{lang_key}', '')}?",
                    "answer": f"{scheme.get(f'description{lang_key}', '')} {steps_text}",
                    "source": scheme.get("apply_url", ""),
                    "type": "scheme",
                })

            if len(context_entries) >= max_results:
                break

        return context_entries

    except Exception as e:
        logger.error(f"ChromaDB query failed: {e}")
        return []


def get_recent_digest_context() -> str:
    """Return today's digest as additional context for timely questions."""
    try:
        from app.services import database as db
        digest = db.get_latest_digest_from_db()
        if digest:
            return f"Today's agricultural news:\n{digest.get('english', digest.get('kannada', ''))}"
    except Exception:
        pass
    return ""
