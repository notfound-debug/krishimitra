# KrishiMitra v4 — Final Fixes & Features

## 3 Parallel Tabs

---

## TAB 1: Android — Home page personalization + stat cards + date stamps
**Files:** Only Android files

### Task 1.1: Personalize home page from profile
File: `android/.../ui/screens/HomeScreen.kt`
- Hero price card: show farmer's PRIMARY crop price (from PrefsStore.crops, first item)
- Currently hardcoded "ಅಡಿಕೆ • ಮಂಗಳೂರು APMC" and "₹42,000"
- Should fetch real price from API for the farmer's primary crop
- Show farmer's taluk in header instead of generic "ದಕ / ಉಡುಪಿ"

### Task 1.2: Replace 3 stat cards
Current: hardcoded Coconut price, Weather, Schemes count
New:
- Card 1: Secondary crop price (farmer's 2nd crop from profile). Show real price.
- Card 2: Real weather from OpenWeatherMap API (farmer has the key). Show temp + rain/no rain.
- Card 3: Smart insight — "💡 ಸಲಹೆ" with crop name + actionable advice based on price trend.

Need:
- Create a weather endpoint `GET /api/weather?lat=X&lon=X` in backend (TAB 2 handles this)
- HomeViewModel needs to fetch prices + weather on load
- Stat cards need to be dynamic, not hardcoded

### Task 1.3: Date stamps on prices
File: `android/.../ui/screens/PricesScreen.kt`
- Add date below hero price card: "📅 19 ಮಾರ್ಚ್ 2026 ರ ಬೆಲೆ"
- If prices are from mock/fallback: "📅 ಅಂದಾಜು ಬೆಲೆ (Estimated)"
- The PricesResponse already has a `date` field — just display it

### Task 1.4: Add Copra + Banana to price chips
File: `android/.../ui/screens/PricesScreen.kt`
- Add CropChip("Copra", "ಕೊಬ್ಬರಿ", "🥥")
- Add CropChip("Banana", "ಬಾಳೆ", "🍌")

### Build:
```bash
cd D:\code\krish\android && bash gradlew assembleDebug
```

---

## TAB 2: Backend — Weather API + Copra/Banana scraper + separate LLM chains
**Files:** Only backend files

### Task 2.1: Weather endpoint
New file: `backend/app/routers/weather.py`
- `GET /api/weather?district=Dakshina Kannada`
- Uses OpenWeatherMap API (key in settings.openweather_api_key)
- District → lat/lon mapping for DK, Udupi, UK
- Returns: { temp, feels_like, description, rain_forecast, humidity, wind }
- Kannada weather descriptions

Register in main.py.

### Task 2.2: Add Copra + Banana to price scraper
File: `backend/app/services/prices.py`
- Add to COMMODITY_ONLINE_SLUGS: "copra": "copra", "banana": "banana"
- Add to CROP_NAME_KN: "copra": "ಕೊಬ್ಬರಿ", "banana": "ಬಾಳೆ"
- Add to CROP_DISPLAY_NAME, CROP_TO_COMMODITY mappings

### Task 2.3: Separate LLM chains (Groq for digest, Gemini for chat)
File: `backend/app/services/llm.py`
- Add `generate_digest(prompt)` — tries Groq first, then Gemini, then Sarvam
- Keep `generate(prompt)` for chat — tries Gemini first, then Groq, then Sarvam
- Update `backend/app/services/digest.py` to call `generate_digest()` instead of `generate()`
- Update `backend/app/services/ask.py` to keep using `generate()` (Gemini first for chat)

### Task 2.4: Fix article_links being empty
File: `backend/app/services/news.py`
- Check that Article.link is being populated from RSS entries
- The fetch_articles() should include entry.get("link", "")

### Test:
```bash
cd D:\code\krish\backend
python -m uvicorn app.main:app --port 8000
curl http://localhost:8000/api/weather?district=Dakshina%20Kannada
curl http://localhost:8000/api/prices/Copra
curl http://localhost:8000/api/prices/Banana
```

---

## TAB 3: Backend — Smart insight endpoint
**Files:** Only new backend files (no overlap with TAB 2)

### Task 3.1: Smart insight endpoint
New file: `backend/app/routers/insight.py`
- `GET /api/insight?crop=arecanut&language=kn`
- Logic (NO LLM needed — pure data analysis):
  1. Get today's price for the crop
  2. Get yesterday's price (from date-seeded or cached)
  3. Calculate trend (up/down/stable, by how much)
  4. Generate simple rule-based insight:
     - Price rising 3+ days → "ಇನ್ನೂ 2 ದಿನ ಕಾಯಿರಿ, ಬೆಲೆ ↑ ಸಾಧ್ಯ"
     - Price falling → "ಈಗ ಮಾರಿ, ಬೆಲೆ ↓ ಆಗಬಹುದು"
     - Rain coming + crop = arecanut → "ಮಳೆ ಬರುತ್ತಿದೆ, ಸಿಂಪಡಣೆ ಮುಂದೂಡಿ"
     - Stable → "ಬೆಲೆ ಸ್ಥಿರ, ಸಾಮಾನ್ಯ ಸಮಯದಲ್ಲಿ ಮಾರಿ"
  5. Always prefix with crop emoji + name
- Response: { crop, crop_kn, insight_kn, insight_en, trend_direction }

Register in main.py.

### Test:
```bash
curl http://localhost:8001/api/insight?crop=arecanut&language=kn
```

---

## FILE OWNERSHIP (no conflicts)

| File | Tab |
|------|-----|
| `android/**/*.kt` (ALL Android) | Tab 1 ONLY |
| `backend/app/routers/weather.py` | Tab 2 ONLY |
| `backend/app/services/prices.py` | Tab 2 ONLY |
| `backend/app/services/llm.py` | Tab 2 ONLY |
| `backend/app/services/digest.py` | Tab 2 ONLY |
| `backend/app/services/news.py` | Tab 2 ONLY |
| `backend/app/routers/insight.py` | Tab 3 ONLY |
| `backend/app/main.py` | Tab 2 adds weather, Tab 3 adds insight (COORDINATE: Tab 3 waits for Tab 2 to edit main.py first, then Tab 3 adds its line) |

---

## AFTER ALL TABS DONE

### Build + Deploy:
```bash
cd D:\code\krish\android && bash gradlew assembleDebug
```

```powershell
cd D:\krishi-deploy
Remove-Item -Recurse -Force app
xcopy D:\code\krish\backend\app D:\krishi-deploy\app\ /E /I
xcopy D:\code\krish\backend\requirements.txt D:\krishi-deploy\ /Y
git add .
git commit -m "v4: weather, smart insight, personalized home, date stamps"
git push space master:main --force
```

### Regenerate digest:
```
curl -X POST https://sisaram-krish.hf.space/api/digest/generate
```

### Commit to GitHub:
```bash
cd D:\code\krish
git add .
git commit -m "v4: weather, insight, personalized home, 7 crops"
git push
```
