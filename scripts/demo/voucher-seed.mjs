// Voucher C1 sample data for the demo seed. The seed never changes a voucher or request that already exists:
// a voucher whose code is taken is only compared with the state the sample expects, and any difference is
// reported, because a matching code does not prove the seed created it.

const day = 24 * 60 * 60 * 1000;

/** Display status a sample voucher has once its actions ran: [] is DRAFT, the last action decides otherwise. */
export function expectedStatus(actions, startsIn) {
  const last = actions.at(-1);
  if (!last) return 'DRAFT';
  if (last === 'publish') return startsIn > 0 ? 'SCHEDULED' : 'ACTIVE';
  return { pause: 'PAUSED', cancel: 'CANCELLED' }[last];
}

/**
 * api: { list(page, size) -> vouchers, create(body) -> voucher, act(id, action, version) -> { ok, status, json } }.
 * Returns { blocked, differs }: refusals for vouchers this run created, and existing codes in another state.
 */
export async function seedVouchers(api, store, items, now = Date.now()) {
  const at = (offset) => new Date(now + offset).toISOString();
  const existing = new Map();
  for (let page = 0; ; page += 1) {
    const batch = await api.list(page, 50);
    for (const voucher of batch) existing.set(voucher.code, voucher);
    if (batch.length < 50) break;
  }
  const blocked = [];
  const differs = [];
  for (const [code, name, discountType, discountValue, minOrderAmount, maxDiscount, actions, startsIn = -day] of items) {
    const want = expectedStatus(actions, startsIn);
    const found = existing.get(code);
    if (found) {
      if (found.status !== want) differs.push(`${store} ${code}: is ${found.status}, sample expects ${want}; left unchanged`);
      continue;
    }
    let voucher = await api.create({
      code, name, discountType, discountValue, minOrderAmount, usageLimit: 100,
      startsAt: at(startsIn), endsAt: at(startsIn + 60 * day), ...(maxDiscount ? { maxDiscount } : {}),
    });
    for (const action of actions) {
      const reply = await api.act(voucher.id, action, voucher.version);
      if (!reply.ok) { blocked.push(`${store} ${code} ${action}: ${reply.status} ${JSON.stringify(reply.json)}`); break; }
      voucher = reply.json;
    }
  }
  return { blocked, differs };
}

/**
 * api: { latest() -> newest request or undefined, create(body, key) -> { ok, status, text } }.
 * Files a request only for a Store that never had one. A Store holds at most one PENDING request and cannot
 * file another while it waits, so the newest request tells whether one is PENDING; a decided request is
 * the Admin's history and is not replaced.
 */
export async function seedLimitRequest(api, store, body, now = Date.now()) {
  const latest = await api.latest();
  if (latest?.status === 'PENDING') return { blocked: [], differs: [] };
  if (latest) return { blocked: [], differs: [`${store} limit request: newest is ${latest.status}, no PENDING request; left unchanged`] };
  // A fresh key per attempt: if a run dies after the insert, the next run finds that PENDING row above.
  const reply = await api.create(body, `seed-limit-${now}`);
  return reply.ok ? { blocked: [], differs: [] } : { blocked: [`${store} limit request: ${reply.status} ${reply.text}`], differs: [] };
}
