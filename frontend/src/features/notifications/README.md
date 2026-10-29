# Notifications — TV4

`NotificationCenter` is a presentational, independently testable component.
It only renders messages supplied by an approved host API and delegates the
read action through `onMarkRead`. It does not invent an event schema,
recipient lookup, authentication, retry policy or backend endpoint.

Build preview/library: `npm run build -- --config src/features/notifications/vite.notifications.config.ts`.
