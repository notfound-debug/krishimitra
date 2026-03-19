# KrishiMitra v3 Implementation Plan

## Parallel Execution Guide

**3 tabs recommended** — each works on independent files with zero overlap.

---

## TAB 1: Android UI + Nav Bar + Onboarding
**Focus:** All Android Kotlin/Compose files
**No backend changes**

### Task 1.1: Update font sizes across all screens
Files to modify:
- `android/app/src/main/java/com/krishi/app/ui/screens/HomeScreen.kt`
- `android/app/src/main/java/com/krishi/app/ui/screens/ChatScreen.kt`
- `android/app/src/main/java/com/krishi/app/ui/screens/PricesScreen.kt`
- `android/app/src/main/java/com/krishi/app/ui/screens/SchemesScreen.kt`

Font spec (approved v3.1):
```
Body/news text:     13sp → 16sp
Action lines:       12sp → 15sp
Badges:             9sp  → 12sp
Source links:       10sp → 12sp
Chat bubbles:       13sp → 16sp
Suggestion chips:   12sp → 15sp
Stat card values:   15sp → 16sp
Stat card labels:   10sp → 12sp
Section titles:     10sp → 13sp
Header subtitle:    10sp → 12sp
Hero price label:   9sp  → 13sp
Hero price number:  30sp → 26sp
Hero price unit:    11sp → 13sp
Hero trend:         11sp → 12sp
```

### Task 1.2: Update nav bar in KrishiMitraApp.kt
File: `android/app/src/main/java/com/krishi/app/ui/KrishiMitraApp.kt`

Changes:
- Nav background: `#071207` → `#1B3A1E` (softer dark green)
- Inactive icon color: `#4CAF50` at 0.5 opacity → `#A5D6A7` at 1.0 opacity
- Active color: keep `#C6FF00` (lime green)
- Icon size: 15sp → 22sp
- Label size: 9sp → 13sp
- Nav item icon box: 28dp → 36dp
- Nav bar height: increase padding

Update `KrishiColors` in `ui/theme/KrishiTheme.kt`:
- Add `NavBackground = Color(0xFF1B3A1E)`
- Add `NavInactive = Color(0xFFA5D6A7)`

### Task 1.3: Build Onboarding screen (not skippable)
New files:
- `android/app/src/main/java/com/krishi/app/ui/screens/OnboardingScreen.kt`

Onboarding flow:
1. Welcome card: "ಕೃಷಿಮಿತ್ರಗೆ ಸ್ವಾಗತ!" with app description
2. Feature cards: explain each tab (swipe through 3-4 cards)
3. Profile setup form:
   - District dropdown: ದಕ್ಷಿಣ ಕನ್ನಡ, ಉಡುಪಿ, ಉತ್ತರ ಕನ್ನಡ
   - Taluk dropdown (changes based on district)
   - Crops multi-select chips: ಅಡಿಕೆ, ತೆಂಗು, ಭತ್ತ, ಮೆಣಸು, ಗೋಡಂಬಿ, ಕೋಕೋ, ರಬ್ಬರ್
   - Land size input (acres)
   - Language: ಕನ್ನಡ / English
4. "ಸಿದ್ಧ — ಆರಂಭಿಸೋಣ!" button → saves profile → enters app

Storage: Save to PrefsStore (isOnboarded=true) + POST /api/profile to Supabase

Modify files:
- `android/app/src/main/java/com/krishi/app/ui/KrishiMitraApp.kt` — check isOnboarded, show OnboardingScreen if false
- `android/app/src/main/java/com/krishi/app/data/local/PrefsStore.kt` — add isOnboarded flag, district, crops fields

### Task 1.4: Profile editing in Schemes screen
File: `android/app/src/main/java/com/krishi/app/ui/screens/SchemesScreen.kt`

Changes:
- Make the profile bar tappable → opens a bottom sheet or dialog with the same form as onboarding
- On save → update PrefsStore + POST /api/profile
- Schemes list re-filters based on new profile

### Task 1.5: Connect profile to all screens
- HomeScreen: hero price card shows farmer's primary crop (from profile)
- PricesScreen: default selected crop from profile
- SchemesScreen: filter schemes by profile (district, crops, land_acres)
- ChatScreen: pass district + crops to API for personalized answers
- Header: show farmer's taluk instead of generic "ದಕ / ಉಡುಪಿ"

### Build & test:
```bash
cd D:\code\krish\android
bash gradlew assembleDebug
```

---

## TAB 2: Backend — Prices (scraping + commodity fix)
**Focus:** Backend price service only
**Files:** `backend/app/services/prices.py`, `backend/app/routers/prices.py`
**No Android changes**

### Task 2.1: Fix data.gov.in commodity name
File: `backend/app/services/prices.py`

Change the commodity mapping:
```
"arecanut" → "Arecanut(Betelnut/Supari)"   (was "Arecanut(Whole)")
"coconut"  → "Coconut"
"rice"     → "Paddy(Dhan)(Common)"
"pepper"   → "Black Pepper"  (try also "Pepper Black")
"cashew"   → "Cashewnuts"   (try also "Cashew Nut")
```

Test with: `curl https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070?api-key=KEY&format=json&filters[State]=Karnataka&filters[Commodity]=Arecanut(Betelnut/Supari)&limit=5`

### Task 2.2: Add commodityonline.com scraper as fallback
File: `backend/app/services/prices.py`

URL pattern: `https://www.commodityonline.com/mandiprices/{commodity-slug}/karnataka/{market}`

Commodity slugs:
- arecanut: `arecanut-betelnutsupari`
- coconut: `coconut`
- pepper: `black-pepper`
- cashew: `cashewnuts`
- rice: `paddy-dhan-common`

Markets: `puttur`, `mangalore`, `sullia`, `bantwal`, `udupi`

Scrape with httpx + parse HTML for price table. Extract min/max/modal prices.

Waterfall:
```python
async def fetch_prices(crop):
    # 1. Try data.gov.in
    prices = await _try_data_gov(crop)
    if prices: return prices

    # 2. Try commodityonline.com
    prices = await _try_commodityonline(crop)
    if prices: return prices

    # 3. Fallback to date-seeded
    return _generate_seeded_prices(crop)
```

Add `beautifulsoup4` to requirements.txt if not present.

### Test:
```bash
cd D:\code\krish\backend
python -m uvicorn app.main:app --port 8000
curl http://localhost:8000/api/prices/Arecanut
```

---

## TAB 3: Backend — News System (keyword-based, less LLM)
**Focus:** Backend digest/news service only
**Files:** `backend/app/services/digest.py`, `backend/app/services/news.py`
**No Android changes, no price changes**

### Task 3.1: Keyword-based categorization (no LLM)
File: `backend/app/services/news.py`

Add function:
```python
def categorize_article(title: str, summary: str) -> str:
    """Categorize article WITHOUT LLM — pure keyword matching."""
    text = (title + " " + summary).lower()
    if any(w in text for w in ["price", "₹", "rate", "msp", "ಬೆಲೆ", "mandi"]):
        return "price"
    if any(w in text for w in ["disease", "pest", "ರೋಗ", "ಕೀಟ", "spray", "crop"]):
        return "crop"
    if any(w in text for w in ["scheme", "subsidy", "ಯೋಜನೆ", "pm kisan", "insurance"]):
        return "scheme"
    if any(w in text for w in ["rain", "weather", "ಮಳೆ", "monsoon", "cyclone", "flood"]):
        return "weather"
    if any(w in text for w in ["diesel", "petrol", "fuel", "transport", "export ban", "policy"]):
        return "impact"
    return "general"
```

### Task 3.2: Restructure digest generation
File: `backend/app/services/digest.py`

New flow:
```
1. Fetch articles from RSS (FREE — no LLM)
2. Categorize each with keywords (FREE — no LLM)
3. Filter relevant ones (top 7-10 by relevance score) (FREE)
4. Format as structured items with title + summary (FREE)
5. ONE small LLM call: generate action lines only
   Input: just the 7-10 titles (not full articles)
   Output: 7-10 action lines starting with →
   Token usage: ~300 input + ~200 output = ~500 total (was ~5000)
6. Combine articles + action lines → save as digest
```

This cuts LLM usage by 90% for digests.

### Task 3.3: Regenerate digest on deployed backend
After deploying, call:
```
curl -X POST https://sisaram-krish.hf.space/api/digest/generate
```
This generates fresh digest with article_links included → fixes the "links not clickable" issue.

### Test:
```bash
cd D:\code\krish\backend
python -m uvicorn app.main:app --port 8001  # different port from Tab 2
curl -X POST http://localhost:8001/api/digest/generate
curl http://localhost:8001/api/digest
# Verify: article_links present, categories correct, action lines exist
```

---

## AFTER ALL TABS DONE

### Merge & Build:
```bash
cd D:\code\krish\android
bash gradlew assembleDebug
```

### Deploy backend:
```powershell
cd D:\krishi-deploy
Remove-Item -Recurse -Force app
xcopy D:\code\krish\backend\app D:\krishi-deploy\app\ /E /I
xcopy D:\code\krish\backend\requirements.txt D:\krishi-deploy\ /Y
cd D:\krishi-deploy
git add .
git commit -m "v3: readable UI, onboarding, real prices, keyword news"
git push space master:main --force
```

### After deploy is running:
```
curl -X POST https://sisaram-krish.hf.space/api/digest/generate
```

### Install APK on phone and test.

---

## FILE OWNERSHIP (no conflicts between tabs)

| File | Tab |
|------|-----|
| `android/.../*.kt` (ALL Android files) | Tab 1 ONLY |
| `backend/app/services/prices.py` | Tab 2 ONLY |
| `backend/app/routers/prices.py` | Tab 2 ONLY |
| `backend/requirements.txt` | Tab 2 ONLY |
| `backend/app/services/digest.py` | Tab 3 ONLY |
| `backend/app/services/news.py` | Tab 3 ONLY |
| `backend/app/config.py` | Tab 3 ONLY |
| `backend/app/main.py` | NONE (no changes needed) |
| `backend/app/services/llm.py` | NONE (no changes needed) |
| `backend/app/services/ask.py` | NONE (no changes needed) |
| `backend/app/services/knowledge.py` | NONE (no changes needed) |

Zero file conflicts. Each tab works independently.
