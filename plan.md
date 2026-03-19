# KrishiMitra - AI Agricultural Assistant for Karnataka Farmers

## Context

**The Problem:** Karnataka has 8.68 million farm holdings, 80% of which are small/marginal farmers. Only 11% receive any advisory from traditional extension services (agent-to-farmer ratio is 1:1,000 to 1:5,000). Coastal Karnataka (DK, Udupi, Uttara Kannada) faces a specific crisis: 60% of arecanut palms infected with Kole Roga/inflorescence die-back, plus cyclone vulnerability and limited crop diversification. Farmers make critical decisions based on incomplete information, while valuable agri news, market prices, and scientific advice exist scattered across Kannada/English sources they don't access.

**The Idea:** Build a free Android app that:
1. Fetches agricultural news from Karnataka-specific sources daily and presents it as an actionable Kannada digest
2. Lets farmers ask questions in Kannada/English and get AI-powered answers using a curated agri knowledge base (RAG)
3. Shows real-time mandi prices across nearby APMCs
4. Helps farmers find eligible government schemes
5. Works smoothly on slow rural internet connections

**Target:** Coastal Karnataka first (Dakshina Kannada / Udupi) -- arecanut, coconut, rice, pepper, cashew farmers.

**Why now:** Free LLM APIs (Gemini, Sarvam AI) + free Indic language models + smartphone penetration in coastal Karnataka make this viable at near-zero cost.

---

## Part 1: Competitive Landscape

### What Exists & Why It's Not Enough

| Product | Users | Gap |
|---------|-------|-----|
| **Farmer.Chat** (Digital Green) | 830K | No Kannada. 66% unanswered queries. Not in Karnataka |
| **KissanAI** | Early | Not Kannada-native, not news-driven |
| **BharatAgri** | 1M+ | Paid (Rs 49/mo). Generic all-India, not hyper-local |
| **DeHaat** | 2M+ | Needs 11K+ physical centers. Not in Karnataka |
| **Plantix** | 15M+ | Disease-only (no news, prices, schemes) |
| **AgroStar** | 5M+ | Commerce-first, not information-first |
| **KKISAN** (Govt KA) | - | One-way SMS, no AI, no conversational interface |
| **AgriApp** (Bengaluru) | - | Not AI-powered, limited advisory |

### Why Agri-AI Startups Fail

| Pattern | Example | Our Counter |
|---------|---------|-------------|
| No product-market fit | Gro Intelligence ($115M, shut down) | Start with 10 farmers, iterate on real needs |
| Frequent pivots | Gramophone (-69% revenue) | One focus: news-driven advisory |
| Farmers can't pay | Most farmer apps | Stay free. Monetize B2B later |
| English-first | Most apps | Kannada-native from day 1 |
| Complex onboarding | Many apps | Minimal registration, WhatsApp-shareable APK |

### The Gap We Fill

**Nobody does Kannada-native, news-driven, AI agricultural advisory for Coastal Karnataka farmers.** Specifically:
1. No app converts daily Karnataka agri news into actionable Kannada farmer advice
2. Farmer.Chat supports Hindi/Telugu but NOT Kannada
3. Generic all-India apps don't cover coastal crops (arecanut, coconut, pepper), local schemes, or DK/Udupi APMC prices
4. No app focuses on the arecanut disease crisis devastating coastal Karnataka

---

## Part 2: Target Region -- Coastal Karnataka

### Why Coastal Karnataka First
- Higher smartphone penetration than North KA (better for app adoption)
- Arecanut disease crisis (Kole Roga, 60% infection) creates **urgent demand** for advice
- Plantation farmers tend to be more tech-savvy
- Active APMCs (Udupi has 33% of state arecanut market)
- Dense farming community with strong WhatsApp group culture (940+ KA farmer groups documented)

### Crop Focus
| Crop | Importance | Key Information Needs |
|------|-----------|----------------------|
| **Arecanut** | #1 crop in DK/Udupi | Disease management (Kole Roga, Yellow Leaf), market prices, processing techniques |
| **Coconut** | Major crop | Pest management, copra prices, oil extraction, intercropping |
| **Rice (Paddy)** | Staple, Kharif | Sowing timing, variety selection, monsoon advisory |
| **Black Pepper** | High-value | Price volatility, disease (quick wilt), organic certification |
| **Cashew** | Coastal crop | Processing, export quality, Goan vs local varieties |
| **Cocoa** | Growing crop in DK | Intercropping with arecanut, fermentation, buyer connections |
| **Rubber** | Some areas | Tapping schedules, price trends |

### Key APMCs
- Mangalore (Dakshina Kannada) -- primary market
- Puttur, Sullia, Bantwal, Belthangady (DK taluks)
- Udupi, Kundapura, Karkala, Brahmavar (Udupi taluks)
- Sirsi, Siddapur, Kumta (Uttara Kannada -- if expanding)

---

## Part 3: Data Sources

### Tier 1: Government APIs (Structured, Legal, Free)
| Source | Data | Access |
|--------|------|--------|
| Karnataka OGD Portal | Agriculture datasets | REST API with key (karnataka.data.gov.in/apis) |
| Crop Survey API | Crop survey data | REST API (kodi.karnataka.gov.in) |
| data.gov.in APMC | Market prices by commodity/district | API access |
| Agmarknet | Daily mandi prices | Structured web reports (scraping) |

### Tier 2: News RSS Feeds (Legal, Free)
| Source | Language | Why |
|--------|----------|-----|
| **Krishi Jagran Kannada** | Kannada | Only dedicated Kannada agri publication with digital presence |
| Krishi Jagran English | English | National agri news |
| Deccan Herald Agriculture | English | Karnataka coverage, RSS available |
| Rural Voice | English | Agritech, agribusiness |
| PIB Agriculture | English/Hindi | Government announcements |

### Tier 3: Knowledge Base (One-time ingestion into RAG)
- ICAR-CPCRI (Central Plantation Crops Research Institute, Kasaragod -- near DK) bulletins
- UAS Bangalore / UAHS Shimoga crop advisories
- Journal of Farm Sciences (free open access)
- Karnataka scheme documents (Raitha Siri, Krishi Bhagya, PMFBY)
- Arecanut/coconut disease management guides
- MSP tables, crop calendars

### Tier 4: Real-time Data
- Krishi Marata Vahini (daily market reports, DK/Udupi mandis)
- Weather: OpenWeatherMap free tier (1000 calls/day) or IMD
- Soil health card data from FRUITS portal

---

## Part 4: Architecture

### System Design

```
                         ANDROID APP (Kotlin + Jetpack Compose)
                         |- Local Room DB (cached news, prices, Q&A history)
                         |- Background WorkManager (sync when online)
                         |- Minimal UI, text-first, <2MB payload per sync
                                    |
                                    | HTTPS (compressed JSON)
                                    |
                         BACKEND API (Python FastAPI)
                         Hosted on: HuggingFace Spaces (FREE, 2 vCPU, 16GB RAM)
                                    |
              +--------------------+--------------------+
              |                    |                    |
     RAG ENGINE            LLM GATEWAY          DATA SERVICES
     ChromaDB +            Gemini Flash-Lite    |- RSS Fetcher
     BGE-M3 embeddings     (1000 req/day free)  |- Mandi Price API
     Agri knowledge base   Sarvam 105B/30B      |- Weather API
                           (free chat fallback)  |- Scheme DB
              |                    |                    |
              +--------------------+--------------------+
                                    |
                         DAILY CRON (GitHub Actions - FREE)
                         |- Fetch RSS feeds
                         |- Scrape mandi prices
                         |- Embed new content -> ChromaDB
                         |- Pre-generate daily digest
                         |- Store in Supabase (500MB free)
```

### Slow Internet Optimization Strategy

| Technique | How |
|-----------|-----|
| **Pre-fetch daily digest** | GitHub Actions generates digest at 5 AM. App fetches one small JSON payload on first open (~5-10KB) |
| **Cache everything locally** | Room DB stores news, prices, Q&A history. Show cached content instantly, update in background |
| **Compress API responses** | gzip all JSON responses. Text-only, no images in API (load images lazily from CDN if needed) |
| **Background sync** | WorkManager syncs prices and news when WiFi/good connection available |
| **Paginate Q&A** | Don't load full chat history over network. Keep locally, sync metadata only |
| **Offline Q&A history** | Previous questions and answers always available offline |
| **Graceful degradation** | If no internet: show cached content with "Last updated: X hours ago" badge |
| **Minimal app size** | Target < 15MB APK. No heavy libraries or embedded models |

### Tech Stack

| Layer | Choice | Cost | Why |
|-------|--------|------|-----|
| **Android App** | Kotlin + Jetpack Compose | Free | Native performance, best offline support (Room DB, WorkManager), Material 3, Kannada rendering built-in |
| **Backend** | Python FastAPI | Free | Fast, async, perfect for LLM orchestration |
| **Hosting** | HuggingFace Spaces (Docker) | Free | 2 vCPU, 16GB RAM, always-on for public apps |
| **Primary LLM** | Google Gemini Flash-Lite API | Free | 1000 req/day, native Kannada, no credit card |
| **Fallback LLM** | Sarvam 105B/30B | Free | Free chat completion, purpose-built for Indian languages |
| **Embeddings** | BGE-M3 (run on HF Spaces) | Free | 100+ languages including Kannada |
| **Vector DB** | ChromaDB (embedded in backend) | Free | Zero config, pip install, fast |
| **Translation** | Sarvam Translate API | Free credits (Rs 1000) | Best Kannada quality. Fallback: IndicTrans2 (open source) |
| **App Database** | Room (on-device SQLite) | Free | Offline cache for news, prices, Q&A |
| **Server Database** | Supabase | Free (500MB) | User profiles, query logs, analytics |
| **Scheduling** | GitHub Actions | Free (public repo) | Daily news ingestion, price fetching |
| **Code Hosting** | GitHub | Free | Version control, CI/CD |
| **App Distribution** | Direct APK + Play Store ($25 one-time) | ~Free | Sideload for testing, Play Store for scale |

---

## Part 5: All-in-One MVP Features

### Feature 1: Daily News Digest
- Backend cron fetches 10-20 articles from RSS feeds at 5 AM
- LLM summarizes into 5-7 bullet points, categorized: prices, weather, schemes, diseases, general
- Pre-generated in Kannada and English, stored as JSON
- App fetches digest on open (~5KB payload)
- Cached locally -- readable offline all day
- Push notification: "Today's farm news is ready"

### Feature 2: Ask KrishiMitra
- Chat interface -- farmer types/speaks question in Kannada or English
- Backend: RAG retrieves relevant chunks from knowledge base + recent news
- LLM generates contextual answer with source citations
- Previous Q&A cached locally (always readable offline)

### Feature 3: Mandi Prices
- Select crop -> see prices across 5-8 nearby APMCs
- Daily trend indicator (up/down/stable arrow)
- MSP comparison where applicable
- "Best mandi to sell at" recommendation
- Prices cached for the day -- one fetch, view anytime

### Feature 4: Scheme Finder
- Answer 3-4 questions: crop, land size, farmer category, district
- Get personalized list of eligible schemes
- Schemes database stored locally (updated weekly via sync)

### Feature 5: Weather Advisory
- Taluk-level weather forecast (3-5 day)
- Farming-specific advisory
- Monsoon onset/withdrawal tracking
- Cyclone alerts for coastal region

---

## Part 6: Implementation Roadmap

### Phase 1: Foundation (Week 1-2)
**Backend:**
- [ ] Set up FastAPI project on HuggingFace Spaces
- [ ] Integrate Gemini Flash-Lite API with Kannada prompt engineering
- [ ] Set up Sarvam API as fallback
- [ ] Build basic `/ask` endpoint (question in -> answer out)
- [ ] Set up ChromaDB with 50 arecanut/coconut documents
- [ ] Test: send Kannada question, get Kannada answer

**Android:**
- [ ] Set up Kotlin + Jetpack Compose project
- [ ] Build chat UI screen (Material 3)
- [ ] Room DB setup for caching
- [ ] Retrofit client to hit backend API
- [ ] Test on emulator: type question -> get answer displayed

### Phase 2: Core Features (Week 3-4)
**Backend:**
- [ ] RSS feed ingestion pipeline (Krishi Jagran Kannada, Deccan Herald)
- [ ] Daily digest generation (LLM summarization)
- [ ] Mandi price fetcher (data.gov.in API for DK/Udupi APMCs)
- [ ] GitHub Actions cron: daily at 5 AM IST
- [ ] `/digest` endpoint, `/prices/{crop}` endpoint
- [ ] Expand RAG knowledge base to 200+ documents

**Android:**
- [ ] News digest screen (pull-to-refresh, cached locally)
- [ ] Mandi price checker screen (crop selector, price table)
- [ ] Scheme finder screen (form + results)
- [ ] Background sync with WorkManager
- [ ] Offline indicator ("Last updated: X")
- [ ] Kannada/English language toggle

### Phase 3: Polish & Test (Week 5-6)
- [ ] Weather advisory integration
- [ ] Push notifications for daily digest
- [ ] Q&A history (searchable, offline)
- [ ] Error handling for no-network scenarios
- [ ] APK size optimization (target < 15MB)
- [ ] Test with 5-10 farmers from your area

### Phase 4: Distribute & Grow (Week 7-10)
- [ ] Share APK via WhatsApp farmer groups
- [ ] Recruit 50-100 farmers in DK/Udupi
- [ ] Track metrics: daily active users, queries/day, retention
- [ ] Publish on Play Store ($25 one-time)

### Phase 5: Scale (Months 3-6)
- [ ] Expand to Uttara Kannada
- [ ] Add voice input/output (Sarvam STT/TTS API)
- [ ] Fine-tune a small model on collected Q&A data
- [ ] Seek grants: Karnataka Agri Grand Challenge (up to Rs 50 lakh), NABARD
- [ ] Target: 1,000+ active users

---

## Part 7: Project Structure

```
krishi/
├── backend/                      # Python FastAPI backend
│   ├── app/
│   │   ├── main.py              # FastAPI app entry
│   │   ├── routers/
│   │   │   ├── ask.py           # Q&A endpoint
│   │   │   ├── digest.py        # News digest endpoint
│   │   │   ├── prices.py        # Mandi prices endpoint
│   │   │   ├── schemes.py       # Scheme finder endpoint
│   │   │   └── weather.py       # Weather endpoint
│   │   ├── services/
│   │   │   ├── llm.py           # Gemini + Sarvam LLM abstraction
│   │   │   ├── rag.py           # ChromaDB RAG engine
│   │   │   ├── news.py          # RSS feed fetcher + processor
│   │   │   ├── prices.py        # APMC price scraper
│   │   │   └── translation.py   # Sarvam/IndicTrans2 translation
│   │   ├── data/
│   │   │   └── knowledge_base/  # Agricultural PDFs, bulletins
│   │   └── config.py            # API keys, settings
│   ├── requirements.txt
│   ├── Dockerfile               # For HuggingFace Spaces
│   └── README.md
├── android/                      # Kotlin + Jetpack Compose app
│   └── ...
├── .github/
│   └── workflows/
│       └── daily_ingest.yml     # Cron: RSS + prices + digest
└── docs/
    └── api.md                    # API documentation
```
