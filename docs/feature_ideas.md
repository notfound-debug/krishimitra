# KrishiMitra — Feature Ideas & Suggestions

## Priority: Next Build (Before Next Deploy)

### 1. Interesting / Not Interesting Algo (Like/Dislike for News)
- Each news card gets two buttons: "ಆಸಕ್ತಿಕರ" (Interesting) / "ಬೇಡ" (Not Interested)
- On "Not Interested": card fades away, new card from different topic appears
- On "Interesting": we remember this farmer likes this topic category
- Next day: reorder digest — farmer's preferred topics first, disliked topics at bottom
- Storage: simple counter per category per farmer in Supabase user_profiles
  Example: {price: +3, weather: -1, scheme: +2, crop: +5}
- NOT ML — just sorting by preference score. Very feasible.
- Goal: make it feel personalized like Instagram but simple

### 2. Real-time News Refresh
- Currently digest generated once daily at 5 AM
- Add a "refresh" button that regenerates digest on demand
- Each refresh = 1 LLM call (Gemini/Groq/Sarvam fallback)
- Budget: ~50-100 refreshes/day on free tier
- Show "ಹೊಸ ಸುದ್ದಿ ಲೋಡ್ ಆಗುತ್ತಿದೆ..." while refreshing
- Breaking news: if RSS has new articles since last digest, highlight them

### 3. Indirect News with Farmer Impact
- Don't just show agri news — show ANY news that affects farmers
- Examples: petrol price hike (affects motor/pump costs), export bans (affects crop prices),
  policy changes (affects subsidies), weather events (affects harvesting)
- LLM should explain WHY it matters: "ಪೆಟ್ರೋಲ್ ₹5 ಹೆಚ್ಚಳ → ನಿಮ್ಮ ಪಂಪ್ ಖರ್ಚು ₹200/ತಿಂಗಳು ಹೆಚ್ಚಾಗಬಹುದು"

---

## Priority: Future Phases

### 4. Voice Input (Phase 5 in plan)
- Most target farmers won't type Kannada
- Use Sarvam STT API for Kannada speech-to-text
- Farmer speaks question → converted to text → sent to Q&A
- Critical for low-literacy farmers

### 5. Push Notifications
- Daily digest ready notification at 5 AM
- Breaking news alerts for major events
- Price alerts: "ಅಡಿಕೆ ₹45,000 ದಾಟಿದೆ!" when price crosses threshold
- Needs Firebase Cloud Messaging (FCM) setup

### 6. Real-time Breaking News
- Background polling of RSS feeds every 2-4 hours
- If major new article detected, push notification
- Needs: FCM + background worker on HuggingFace

### 7. Farmer Community Features
- Share news cards to WhatsApp directly from app
- "Ask in your area" — farmer posts question, other local farmers can reply
- This is Phase 5+ territory

---

## Technical Notes
- Current free tier budget: Gemini 1K/day + Groq 14.4K/day + Sarvam credits
- Interesting/Not Interesting can be stored in existing Supabase user_profiles table
- All features above are feasible on current zero-cost infrastructure
- Voice input (Sarvam STT) may need paid credits after free tier
