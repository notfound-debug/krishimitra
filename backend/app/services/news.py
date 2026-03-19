"""
News Service — Fetches articles from RSS feeds.

How it works:
1. We have a list of RSS feed URLs (Krishi Jagran Kannada, English, etc.)
2. feedparser downloads and parses each feed
3. We extract: title, summary, link, published date, source name
4. Articles are sorted by date (newest first)
5. We return the latest 20 articles for the LLM to summarize

RSS (Really Simple Syndication) is just XML that news sites publish.
Example: https://krishijagran.com/rss/rss-news.xml
Each <item> in the XML has <title>, <description>, <link>, <pubDate>.
feedparser turns all that XML into simple Python dicts.
"""

import feedparser
import httpx
import logging
from datetime import datetime
from dataclasses import dataclass

from app.config import settings

logger = logging.getLogger(__name__)


@dataclass
class Article:
    """One news article from an RSS feed."""
    title: str
    summary: str      # Short description/first paragraph
    link: str         # URL to full article
    source: str       # Which feed it came from (e.g., "Krishi Jagran Kannada")
    published: str    # Date string (e.g., "2026-03-18")


# Map feed URLs to human-readable source names
FEED_SOURCES = {
    "https://krishijagran.com/feeds/rss": "Krishi Jagran",
    "https://eng.ruralvoice.in/rss/latest-posts": "Rural Voice",
    "https://eng.ruralvoice.in/rss/category/state": "Rural Voice State",
    "https://kannada.krishijagran.com/rss/": "Krishi Jagran Kannada",
    "https://www.prajavani.net/feed": "Prajavani",
    "https://www.vijayavani.net/api/rss": "Vijayavani",
    "https://www.deccanherald.com/feed": "Deccan Herald",
    "https://www.agrifarming.in/feed": "Agri Farming",
}


async def fetch_articles(max_per_feed: int = 10) -> list[Article]:
    """
    Fetch latest articles from all configured RSS feeds.

    Args:
        max_per_feed: Max articles to take from each feed (default 10).
                      With 2 feeds × 10 = up to 20 articles total.

    Returns:
        List of Article objects, newest first.
    """
    all_articles: list[Article] = []

    for feed_url in settings.rss_feeds:
        source_name = FEED_SOURCES.get(feed_url, feed_url)
        try:
            articles = await _fetch_single_feed(feed_url, source_name, max_per_feed)
            all_articles.extend(articles)
            logger.info(f"Fetched {len(articles)} articles from {source_name}")
        except Exception as e:
            logger.warning(f"Failed to fetch {source_name}: {e}")
            # Don't crash — just skip this feed and try the others

    # Sort by date (newest first)
    all_articles.sort(key=lambda a: a.published, reverse=True)

    return all_articles


async def _fetch_single_feed(
    feed_url: str, source_name: str, max_articles: int
) -> list[Article]:
    """
    Fetch and parse one RSS feed.

    Why async HTTP + feedparser?
    feedparser.parse(url) does its own HTTP call, but it's synchronous (blocks).
    Instead, we download the XML ourselves with httpx (async, non-blocking),
    then pass the raw XML to feedparser for parsing. This keeps our server responsive.
    """
    async with httpx.AsyncClient(timeout=15.0) as client:
        resp = await client.get(feed_url)
        resp.raise_for_status()
        raw_xml = resp.text

    # feedparser can parse raw XML string (not just URLs)
    feed = feedparser.parse(raw_xml)

    articles = []
    for entry in feed.entries[:max_articles]:
        # Extract published date — feedparser normalizes it into a struct_time
        published = ""
        if hasattr(entry, "published_parsed") and entry.published_parsed:
            try:
                dt = datetime(*entry.published_parsed[:6])
                published = dt.strftime("%Y-%m-%d")
            except Exception:
                published = entry.get("published", "")

        # Clean up summary (remove HTML tags if any)
        summary = entry.get("summary", entry.get("description", ""))
        # Basic HTML stripping — remove <tags>
        import re
        summary = re.sub(r"<[^>]+>", "", summary).strip()
        # Truncate long summaries to ~500 chars
        if len(summary) > 500:
            summary = summary[:500] + "..."

        # Extract link — some feeds use entry.link, others entry.links[0].href
        link = entry.get("link", "")
        if not link and hasattr(entry, "links") and entry.links:
            link = entry.links[0].get("href", "")

        articles.append(Article(
            title=entry.get("title", "No Title"),
            summary=summary,
            link=link,
            source=source_name,
            published=published,
        ))

    return articles


def categorize_article(title: str, summary: str) -> str:
    """
    Categorize article WITHOUT LLM — pure keyword matching.

    Returns one of: "price", "crop", "scheme", "weather", "impact", "general"
    """
    text = (title + " " + summary).lower()
    # Check impact FIRST — "petrol price" should be impact, not price
    if any(w in text for w in ["diesel", "petrol", "fuel", "transport", "export ban", "policy", "ನೀತಿ", "strike", "tax"]):
        return "impact"
    if any(w in text for w in ["price", "₹", "rate", "msp", "ಬೆಲೆ", "mandi", "market", "ಮಂಡಿ", "quintal"]):
        return "price"
    if any(w in text for w in ["disease", "pest", "ರೋಗ", "ಕೀಟ", "spray", "crop", "ಬೆಳೆ", "variety", "seed", "fertilizer", "bordeaux"]):
        return "crop"
    if any(w in text for w in ["scheme", "subsidy", "ಯೋಜನೆ", "pm kisan", "insurance", "ವಿಮೆ", "grant", "loan", "ಸಾಲ"]):
        return "scheme"
    if any(w in text for w in ["rain", "weather", "ಮಳೆ", "monsoon", "cyclone", "flood", "ಪ್ರವಾಹ", "drought", "ಬರ"]):
        return "weather"
    return "general"


def format_articles_for_llm(articles: list[Article]) -> str:
    """
    Format articles into a text block that the LLM can summarize.

    The LLM receives something like:
    ---
    1. [Krishi Jagran Kannada] ಅಡಿಕೆ ಬೆಲೆ ಏರಿಕೆ
       ಮಂಗಳೂರು ಮಂಡಿಯಲ್ಲಿ ಅಡಿಕೆ ಬೆಲೆ ₹42,000 ತಲುಪಿದೆ...

    2. [Krishi Jagran English] New paddy variety released for coastal Karnataka
       UAHS Shimoga released MO-22 variety resistant to blast disease...
    ---

    The LLM then summarizes all of this into 5-7 Kannada bullet points.
    """
    if not articles:
        return "No articles found today."

    lines = []
    for i, article in enumerate(articles, 1):
        lines.append(
            f"{i}. [{article.source}] {article.title}\n"
            f"   {article.summary}\n"
        )

    return "\n".join(lines)
