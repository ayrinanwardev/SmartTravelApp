# LinkedIn post: Wanderly

Attach: `docs/demo/wanderly-linkedin.mp4` (3:25, 1080×1350, 10.7 MB)

---

✈️ I built Wanderly, a full-stack, real-time travel app, from the Flutter UI down to the Docker image.

The video shows it running on a real Android phone against the production backend container. Everything you see is a live API call, a WebSocket event or a real sandbox payment.

𝗪𝗵𝗮𝘁 𝗶𝘁 𝗱𝗼𝗲𝘀
📍 Discover destinations with search, filters and wishlist-based recommendations
🗓️ Plan trips day by day, with times, costs and drag-to-reorder
💰 Track your budget with charts and live alerts at 80% and when you go over
✨ An assistant that turns "weekend in Sajek for 4 friends under 30k" into a costed plan
🗺️ OpenStreetMap maps of every destination, attraction and hotel
👥 Group trips: friends join with a code, edits sync instantly, and you see each other move on the map
💳 SSLCommerz payments (bKash, Nagad, cards)
🔔 Real-time notifications for bookings, payments, companions and budget

𝗛𝗼𝘄 𝗶𝘁'𝘀 𝗯𝘂𝗶𝗹𝘁
📱 Flutter + Riverpod 3 + GoRouter: feature-first architecture, optimistic updates with rollback, JWT in encrypted storage
⚙️ Kotlin + Spring Boot 4: REST + WebSocket, Spring Security with JWT, Hibernate on Supabase PostgreSQL
🐳 Docker (non-root, health-checked, runs in 512 MB), deployed on Render, CI on GitHub Actions

𝗧𝗵𝗿𝗲𝗲 𝗽𝗿𝗼𝗯𝗹𝗲𝗺𝘀 𝗜 𝗲𝗻𝗷𝗼𝘆𝗲𝗱 𝘀𝗼𝗹𝘃𝗶𝗻𝗴
1️⃣ Never trust a payment redirect. The "success" callback comes from the user's browser, so the server re-validates every transaction with SSLCommerz (amount, currency, transaction ID) before confirming. A test with a forged callback marks the booking as failed.
2️⃣ No phantom updates. Real-time events are only sent after the database transaction commits, so no one sees a change that later rolls back.
3️⃣ 14.8 s → 1.6 s. The home screen was slow because of an N+1 query pattern; Hibernate batch fetching made it about 9× faster.

Live location is opt-in, kept only in server memory and never stored. Access checks run on every REST call and every WebSocket message.

Code, architecture diagrams and tests: https://github.com/ayrinanwardev/SmartTravelApp

I'm looking for Senior Mobile / Flutter Developer roles. If your team is hiring, I'd love to talk 🙌

#Flutter #Dart #Kotlin #SpringBoot #MobileDevelopment #WebSockets #Docker #FullStack #OpenToWork

---

## Notes before posting

- **"Senior" line:** change it to the role and wording you prefer, or remove it.
- **Upload:** use LinkedIn's video upload rather than a link, so it plays in the feed. The video has no audio, which works well for feeds that autoplay muted.
- **Accuracy:**
  - Payments ran in the SSLCommerz **sandbox**.
  - The "assistant" is rule-based, not an LLM, which is why the post doesn't call it "AI-powered".
  - Push with the app closed (FCM) isn't configured yet, so the post says "real-time notifications" rather than "push notifications".
- **Short version** (if you want a shorter post):

> ✈️ Wanderly: a real-time travel app I built end to end. Flutter + Riverpod on mobile, Kotlin + Spring Boot with WebSockets on the backend, PostgreSQL, Docker. Group trips with live location, SSLCommerz payments validated on the server, live notifications. Recorded on a real phone 👇
> https://github.com/ayrinanwardev/SmartTravelApp
> #Flutter #Kotlin #SpringBoot #MobileDevelopment #OpenToWork
