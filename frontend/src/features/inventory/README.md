# Inventory dashboard — TV4

This feature is an independently testable React component for the TV4 inventory view. It accepts verified API data through `InventoryState`; it does not invent an API client, authentication, route, or stock-adjustment mutation.

Quantities remain decimal strings so JavaScript cannot round PostgreSQL `bigint` values. Invalid, duplicated, or negative API rows fail closed. The component is ready for TV1 to mount in the shared router once the Store-scoped Inventory read contract is approved.

Build preview/library: `npm run build -- --config src/features/inventory/vite.inventory.config.ts`.
