// Generates credentials only inside the per-project UAT runtime directory.
// Required: HK5_UAT_ENV_FILE, HK5_UAT_SECRETS_DIR, HK5_UAT_ALLOWED_ORIGINS.
import { randomBytes } from 'node:crypto';
import { chmodSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { isAbsolute, resolve } from 'node:path';

const envFile = process.env.HK5_UAT_ENV_FILE;
const secretsDir = process.env.HK5_UAT_SECRETS_DIR;
const origins = process.env.HK5_UAT_ALLOWED_ORIGINS;
if (!envFile || !secretsDir || !origins || !isAbsolute(envFile) || !isAbsolute(secretsDir)) {
  throw new Error('HK5_UAT_ENV_FILE, HK5_UAT_SECRETS_DIR and HK5_UAT_ALLOWED_ORIGINS must be absolute isolated paths.');
}
if (existsSync(envFile) || existsSync(secretsDir)) throw new Error('Refusing to reuse an existing UAT env/secrets directory.');

const secret = () => randomBytes(32).toString('base64url');
const authorityTokens = ['USER', 'SELLER', 'PRODUCT', 'CART', 'ORDER', 'NOTIFICATION']
  .map((name) => `${name}_AUTHORITY_TOKEN`);
const values = {
  POSTGRES_PASSWORD: secret(), AUTH_DB_PASSWORD: secret(), AUTH_MIGRATION_PASSWORD: secret(),
  USER_DB_PASSWORD: secret(), USER_MIGRATION_PASSWORD: secret(),
  JWT_ISSUER_URI: 'http://auth-service.hk5.local', JWT_AUDIENCE: 'smart-ecommerce-api',
  JWT_HS256_CURRENT_KID: 'uat-key-v1', JWT_HS256_CURRENT_KEY_B64: randomBytes(32).toString('base64'),
  ALLOWED_ORIGINS: origins, ADMIN_BOOTSTRAP_EMAIL: 'admin@hk5.local',
  ADMIN_BOOTSTRAP_PASSWORD: secret().slice(0, 20), SEED_PASSWORD: secret().slice(0, 20),
  INVENTORY_ORDER_PAIR_ENABLED: 'false', HK5_FINANCE_POLICY_APPROVED: 'false',
  HK5_FINANCE_PLATFORM_RATE: '0.05', HK5_FINANCE_CURRENCY_SCALE: '0', HK5_FINANCE_HOUSE_EXEMPT: 'true',
  ORDER_CART_TOKEN: secret(), ORDER_INVENTORY_TOKEN: secret(), ORDER_PRODUCT_TOKEN: secret(), ORDER_SELLER_TOKEN: secret(),
  CART_PRODUCT_TOKEN: secret(), PRODUCT_INVENTORY_TOKEN: secret(), PRODUCT_SELLER_TOKEN: secret(),
  SELLER_AUTH_TOKEN: secret(), SELLER_NOTIFICATION_TOKEN: secret(),
  ...Object.fromEntries(authorityTokens.map((key) => [key, secret()])),
};
const nonSecrets = new Set([
  'JWT_ISSUER_URI', 'JWT_AUDIENCE', 'JWT_HS256_CURRENT_KID', 'ALLOWED_ORIGINS', 'ADMIN_BOOTSTRAP_EMAIL', 'SEED_PASSWORD',
  'INVENTORY_ORDER_PAIR_ENABLED', 'HK5_FINANCE_POLICY_APPROVED', 'HK5_FINANCE_PLATFORM_RATE',
  'HK5_FINANCE_CURRENCY_SCALE', 'HK5_FINANCE_HOUSE_EXEMPT',
]);

mkdirSync(resolve(secretsDir), { recursive: true });
chmodSync(resolve(secretsDir), 0o700);
writeFileSync(resolve(envFile), ['# Generated isolated UAT environment. Never commit.', ...Object.entries(values).map(([k, v]) => `${k}=${v}`)].join('\n') + '\n', { mode: 0o600 });
for (const [name, value] of Object.entries(values)) {
  if (!nonSecrets.has(name)) {
    const file = resolve(secretsDir, name);
    writeFileSync(file, value, { mode: 0o600 });
  }
}
const fenced = { USER: 'user-service:8102', SELLER: 'seller-service:8103', PRODUCT: 'product-service:8104', CART: 'cart-service:8106', ORDER: 'order-service:8107', NOTIFICATION: 'notification-service:8108' };
writeFileSync(resolve(secretsDir, 'WRITE_FENCE_TARGETS'), Object.entries(fenced).map(([name, host]) => `${values[`${name}_AUTHORITY_TOKEN`]}@http://${host}`).join(','), { mode: 0o600 });
console.log(`Generated isolated UAT env and ${Object.keys(values).filter((key) => !nonSecrets.has(key)).length + 1} secret files.`);
