# Android Fixes — Tab 2

## Screenshots Analysis (from farmer testing)

These issues were found by reviewing screenshots of the running app:

### Issue 1: Weather stat card shows "—" instead of real temp
- HomeViewModel calls `ApiClient.service.getWeather(district)`
- The district is stored in Kannada (e.g., "ಉತ್ತರ ಕನ್ನಡ") but API expects English (e.g., "Uttara Kannada")
- Need to map Kannada district to English before API call
- Mapping: "ದಕ್ಷಿಣ ಕನ್ನಡ" → "Dakshina Kannada", "ಉಡುಪಿ" → "Udupi", "ಉತ್ತರ ಕನ್ನಡ" → "Uttara Kannada"
- File: `android/.../ui/viewmodel/HomeViewModel.kt` around line 160

### Issue 2: Insight stat card text overflow / cut off
- The insight card shows "ಸಲಹೆ" as value and insight text as label
- But label is truncated with `.take(15)` which cuts Kannada text mid-word
- Fix: Show "💡" as emoji, insight crop name as value, and short action as label
- Allow label to be up to 20 chars or use smaller font
- File: `android/.../ui/screens/HomeScreen.kt` in StatsGrid composable

### Issue 3: Secondary crop shows "ತೆಂಗಿನಕಾಯಿ" instead of "ತೆಂಗು"
- The crop name comes from the profile (OnboardingScreen stores with emoji like "🥥 ತೆಂಗು")
- After stripping emoji it becomes "ತೆಂಗು" which is correct
- But the price API returns crop_kn which might say "ತೆಂಗಿನಕಾಯಿ"
- Use the shorter name from the profile, not from API
- File: `android/.../ui/viewmodel/HomeViewModel.kt` around line 150

### Issue 4: APMC names in English on Prices screen
- The price table shows "Mangalore", "Puttur" etc. even when language is Kannada
- Need a mapping in PricesScreen: Mangalore → ಮಂಗಳೂರು, Puttur → ಪುತ್ತೂರು, etc.
- Apply this mapping in the PriceTableRow when language == "kn"
- Full mapping:
  - Mangalore → ಮಂಗಳೂರು
  - Puttur → ಪುತ್ತೂರು
  - Sullia → ಸುಳ್ಯ
  - Bantwal → ಬಂಟ್ವಾಳ
  - Belthangady → ಬೆಳ್ತಂಗಡಿ
  - Udupi → ಉಡುಪಿ
  - Kundapura → ಕುಂದಾಪುರ
  - Karkala → ಕಾರ್ಕಳ
- File: `android/.../ui/screens/PricesScreen.kt`

### Issue 5: Date format raw "2026-03-19"
- Hero price card and prices screen show date as "2026-03-19"
- Should format as: "19 ಮಾರ್ಚ್ 2026" (Kannada) or "19 Mar 2026" (English)
- Kannada month names: ಜನವರಿ, ಫೆಬ್ರವರಿ, ಮಾರ್ಚ್, ಏಪ್ರಿಲ್, ಮೇ, ಜೂನ್, ಜುಲೈ, ಆಗಸ್ಟ್, ಸೆಪ್ಟೆಂಬರ್, ಅಕ್ಟೋಬರ್, ನವೆಂಬರ್, ಡಿಸೆಂಬರ್
- Files: `HomeScreen.kt` (HeroPriceCard) and `PricesScreen.kt` (hero card)

### Issue 6: Schemes not filtered by profile
- Currently shows all 9 schemes regardless of farmer's district/crops/land
- Should call `ApiClient.service.getEligibleSchemes(district, crops, landAcres)` instead of `getSchemes()`
- Map Kannada district to English for API: "ಉತ್ತರ ಕನ್ನಡ" → "Uttara Kannada"
- Map Kannada crops to English for API: "ಅಡಿಕೆ" → "Arecanut" etc.
- File: `android/.../ui/screens/SchemesScreen.kt`

## IMPORTANT: Do NOT touch any backend files. Only modify Android files.
