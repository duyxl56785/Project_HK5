// Checks Config, Eureka, Kafka and Redis on the running local stack, including outages.
// It stops and restarts containers. Usage: node scripts/demo/smoke-platform.mjs
import { execSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const env = Object.fromEntries(readFileSync(resolve(root, '.env.demo'), 'utf8').split('\n')
  .filter((line) => line.includes('=') && !line.startsWith('#'))
  .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1).trim()]));
const gateway = process.env.GATEWAY_URL ?? 'http://localhost:18080';
const origin = env.ALLOWED_ORIGINS.split(',')[0];
const sh = (command) => execSync(command, { stdio: ['ignore', 'pipe', 'pipe'] }).toString();
const docker = (action, name) => sh(`docker ${action} hk5-demo-${name}-1`);
const curl = (args) => sh(`docker exec hk5-demo-api-gateway-1 curl -s -m 5 ${args}`);
const kafkaBin = (args) => sh(`docker exec hk5-demo-kafka-1 sh -c "/opt/kafka/bin/${args}"`);
const wait = (ms) => new Promise((done) => setTimeout(done, ms));

async function call(path, { token, method = 'GET', body, headers } = {}) {
  const response = await fetch(gateway + path, {
    method,
    headers: {
      Origin: origin, 'X-CSRF-Protection': '1',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let json = text;
  try { json = text ? JSON.parse(text) : null; } catch { /* keep the raw text */ }
  return { status: response.status, json, text };
}

let fails = 0;
function check(name, got, ...want) {
  const ok = want.includes(got);
  if (!ok) fails += 1;
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name} -> ${got}${ok ? '' : ` (want ${want})`}`);
}
async function until(test, seconds) {
  const deadline = Date.now() + seconds * 1000;
  for (;;) {
    if (await test().catch(() => false)) return true;
    if (Date.now() > deadline) return false;
    await wait(1000);
  }
}

const login = async (email, password) => (await call('/api/auth/login', { method: 'POST', body: { email, password } })).json.accessToken;
const customer = await login('customer@hk5.local', env.SEED_PASSWORD);
const seller = await login('seller1@hk5.local', env.SEED_PASSWORD);
const store = (await call('/api/stores/me', { token: seller })).json;
const products = (await call('/api/seller/products?size=100', { token: seller })).json.products;
const product = products.find((p) => p.status === 'ACTIVE') ?? products[0];
const noticeSeen = async (token, text) => (await call('/api/notifications?size=50', { token })).text.includes(text);

async function placeOrder() {
  await call('/api/cart/items', { token: customer, method: 'POST', body: { productId: product.id, quantity: 1 } });
  const cart = (await call('/api/cart', { token: customer })).json;
  const placed = await call('/api/orders', {
    token: customer, method: 'POST', headers: { 'Idempotency-Key': randomUUID() },
    body: {
      expectedItems: cart.items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
      expectedRevision: cart.revision, shipping: { name: 'An', phone: '0901234567', address: '1 Lê Lợi' },
    },
  });
  return { status: placed.status, orderId: placed.json.orderId };
}

// Config Server and Eureka
check('config server serves shared settings', curl('http://config-server:8888/order-service/default').includes('kafka:9092'), true);
const registry = curl('http://discovery-server:8761/eureka/apps');
for (const name of ['API-GATEWAY', 'AUTH-SERVICE', 'USER-SERVICE', 'SELLER-SERVICE', 'PRODUCT-SERVICE', 'INVENTORY-SERVICE', 'CART-SERVICE', 'ORDER-SERVICE', 'NOTIFICATION-SERVICE']) {
  check(`registered ${name}`, registry.includes(`<name>${name}</name>`), true);
}

// Kafka: order placed, then delivered opens the review
const first = await placeOrder();
check('place order', first.status, 200, 201);
const code = first.orderId.slice(0, 8).toUpperCase();
check('customer notified through Kafka', await until(() => noticeSeen(customer, code), 30), true);
check('store owner notified through Kafka', await until(() => noticeSeen(seller, code), 30), true);
const review = { orderId: first.orderId, productId: product.id, rating: 5, comment: 'Tốt' };
check('review refused before delivery', (await call('/api/reviews', { token: customer, method: 'POST', body: review })).status >= 400, true);
const storeOrder = (await call(`/api/orders/${first.orderId}`, { token: customer })).json.storeOrders[0];
for (const action of ['accept', 'prepare', 'ready', 'dispatch', 'deliver']) {
  check(`seller ${action}`, (await call(`/api/seller/orders/${storeOrder.id}/${action}`, { token: seller, method: 'POST', body: {} })).status, 200);
}
check('review opens after delivered event',
  await until(async () => (await call('/api/reviews', { token: customer, method: 'POST', body: review })).status < 300, 30), true);

// Kafka down: orders still work and notifications arrive after recovery
docker('stop', 'kafka');
try {
  const queued = await placeOrder();
  check('order accepted while Kafka is down', queued.status, 200, 201);
  await wait(8000);
  const held = queued.orderId.slice(0, 8).toUpperCase();
  check('notification held while Kafka is down', await noticeSeen(customer, held), false);
  docker('start', 'kafka');
  check('queued notification delivered after recovery', await until(() => noticeSeen(customer, held), 90), true);
} finally {
  docker('start', 'kafka');
}

// Poison record reaches the dead letter topic
const poison = `poison-${randomUUID()}`;
sh(`docker exec hk5-demo-kafka-1 sh -c "echo ${poison} | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server 127.0.0.1:9092 --topic order.placed.v1"`);
check('poison record reaches order.placed.v1.dlt', await until(async () => {
  const seen = kafkaBin('kafka-console-consumer.sh --bootstrap-server 127.0.0.1:9092 --topic order.placed.v1.dlt --from-beginning --timeout-ms 5000 2>/dev/null || true');
  return seen.includes(poison);
}, 40), true);

// Redis: public catalog is cached, stale during a Seller outage, and optional
const search = `/api/catalog/products?storeId=${store.id}`;
check('catalog read', (await call(search)).status, 200);
check('catalog keys in Redis', sh('docker exec hk5-demo-redis-1 redis-cli --scan --pattern catalog:*').includes('catalog:'), true);
docker('stop', 'seller-service');
try {
  await wait(11000);
  const stale = await call(search);
  check('catalog served stale while Seller is down', stale.status, 200);
  check('stale catalog keeps products', stale.json.total > 0, true);
} finally {
  docker('start', 'seller-service');
}
check('Seller back', await until(async () => (await call('/api/stores/me', { token: seller })).status === 200, 90), true);
docker('stop', 'redis');
try {
  check('catalog works without Redis', (await call(search)).status, 200);
} finally {
  docker('start', 'redis');
}

// Eureka down: services keep calling each other
docker('stop', 'discovery-server');
try {
  check('order flow works without Eureka', (await placeOrder()).status, 200, 201);
} finally {
  docker('start', 'discovery-server');
}
let outages = 0;
for (let i = 0; i < 40; i += 1) {
  if ((await call('/api/categories')).status === 503) outages += 1;
  await wait(1000);
}
check('Gateway stays up while Eureka restarts', outages, 0);

console.log(`\nFAILS=${fails}`);
process.exit(fails ? 1 : 0);
