/**
 * Header link to the login or register page that returns the shopper to the page they are on.
 * On the login and register pages the existing return path is kept.
 */
export function authLink(page: '/login' | '/register', pathname: string, search: string) {
  const next = pathname === '/login' || pathname === '/register'
    ? new URLSearchParams(search).get('next')
    : pathname === '/' ? null : pathname + search;
  return next ? `${page}?next=${encodeURIComponent(next)}` : page;
}
