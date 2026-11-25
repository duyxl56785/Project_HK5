/**
 * Header link to the login or register page that returns the shopper to the page they are on.
 * On the login and register pages the existing return path is kept.
 */
export function authLink(page: '/login' | '/register', pathname: string, search: string) {
  // Any origin works here: only a path that stays on it is carried on.
  const kept = safeNext(new URLSearchParams(search).get('next'), 'http://site.invalid');
  const next = pathname === '/login' || pathname === '/register'
    ? (kept === '/' ? null : kept)
    : pathname === '/' ? null : pathname + search;
  return next ? `${page}?next=${encodeURIComponent(next)}` : page;
}

/**
 * Where to go after login: a path on this site, or home.
 * Checking for "//" alone is not enough: browsers read "/\host" as "//host" and drop tabs and newlines,
 * and React Router falls back to a full page load when the browser refuses a cross-site history entry.
 */
export function safeNext(raw: string | null, origin: string): string {
  if (!raw?.startsWith('/')) return '/';
  try {
    const url = new URL(raw, origin);
    const path = url.pathname + url.search + url.hash;
    return url.origin === origin && !path.startsWith('//') ? path : '/';
  } catch {
    return '/';
  }
}
