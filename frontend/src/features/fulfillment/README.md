# SellerOrder fulfillment — TV4

This independently testable component renders Store-scoped SellerOrder actions from the TV3 lifecycle vocabulary. It does not create a router, API client, authentication, persistence, outbox, COD settlement, or fake data source. The host supplies verified orders and handles `onAction` through the approved backend contract.

Build preview/library: `npm run build -- --config src/features/fulfillment/vite.fulfillment.config.ts`.
