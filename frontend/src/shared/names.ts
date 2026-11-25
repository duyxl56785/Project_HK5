/** Name for an id, or a short "#702BB0C4" label when the name is not known, never the whole UUID. */
export function nameOr(names: Readonly<Record<string, string>> | undefined, id: string, kind: string) {
  return names?.[id] ?? `${kind} #${id.slice(0, 8).toUpperCase()}`;
}
