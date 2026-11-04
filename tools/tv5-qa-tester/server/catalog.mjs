/** Commands are fixed here. HTTP input may select IDs, never executable/arguments. */
const gradle = (project, task, patterns) => ({
  executable: 'java', cwd: 'backend',
  args: ['-cp', 'gradle/wrapper/gradle-wrapper.jar', 'org.gradle.wrapper.GradleWrapperMain',
    `:${project}:${task}`, ...patterns.flatMap(p => ['--tests', p]), '--rerun-tasks', '--no-daemon', '--console=plain'],
  evidence: 'junit', results: `backend/${project}/build/test-results/${task}`
});
const nodeTests = { executable: 'node', cwd: 'frontend',
  args: ['--test', '--test-concurrency=1', 'src/**/*.test.mjs'], evidence: 'tap' };

export const suites = Object.freeze([
  { id: 'F15-RULES', module: 'F15 Review', title: 'Điều kiện buyer, rating và R06', expected: 'Review rules và giới hạn 1000 UTF-16 đạt', safeAll: true, timeoutMs: 180000,
    source: 'backend/product-service/src/test/java/com/projecthk5/productservice/review/',
    command: gradle('product-service', 'test', ['*ReviewRulesTest']) },
  { id: 'F15-POSTGRES', module: 'F15 Review', title: 'Migration review legacy trên PostgreSQL', expected: 'Migration và Admin moderation legacy đạt trên PostgreSQL', safeAll: false, requiresDocker: true, timeoutMs: 240000,
    source: 'backend/product-service/src/test/java/com/projecthk5/productservice/web/ReviewCommentMigrationPostgresTest.java',
    command: gradle('product-service', 'integrationTest', ['*ReviewCommentMigrationPostgresTest']) },
  { id: 'F16-RULES', module: 'F16 Finance', title: 'Commission, snapshot và finance gate', expected: 'Rules 5%/0%, bounds và gate fail-closed đạt trong unit tests', safeAll: true, timeoutMs: 180000,
    source: 'backend/order-service/src/test/java/com/projecthk5/orderservice/finance/',
    command: gradle('order-service', 'test', ['*FinanceRulesTest']) },
  { id: 'F16-POSTGRES', module: 'F16 Finance', title: 'Fulfillment/COD/settlement PostgreSQL', expected: 'Selected PostgreSQL assertions đạt; StoreDirectory vẫn là mock trong test này', safeAll: false, requiresDocker: true, timeoutMs: 300000,
    source: 'backend/order-service/src/test/java/com/projecthk5/orderservice/integration/FulfillmentPostgres16IT.java',
    command: gradle('order-service', 'postgresIntegrationTest', ['*FulfillmentPostgres16IT']) },
  { id: 'SEC-JWT', module: 'Security', title: 'JWT YAML và fail-closed', expected: 'Spring YAML JWT assertions đạt', safeAll: true, timeoutMs: 180000,
    source: 'backend/order-service/src/test/java/com/projecthk5/orderservice/security/JwtEnvironmentConfigurationTest.java',
    command: gradle('order-service', 'test', ['*JwtEnvironmentConfigurationTest']) },
  { id: 'SEC-RBAC', module: 'Security', title: 'Current authority/RBAC filter', expected: 'Unit assertions về current authority đạt', safeAll: true, timeoutMs: 180000,
    source: 'backend/security-common/src/test/java/com/projecthk5/security/CurrentAuthorityFilterTest.java',
    command: gradle('security-common', 'test', ['*CurrentAuthorityFilterTest']) },
  { id: 'CATALOG', module: 'Product/Catalog', title: 'SKU, batch và price validation', expected: 'Catalog batch/price validation đạt', safeAll: true, timeoutMs: 180000,
    source: 'backend/product-service/src/test/java/com/projecthk5/productservice/catalog/CatalogBatchValidatorTest.java',
    command: gradle('product-service', 'test', ['*CatalogBatchValidatorTest', '*ProductPriceTest']) },
  { id: 'INVENTORY', module: 'Order/Inventory', title: 'Inventory operation và input validation', expected: 'Unit assertions về operation và input đạt', safeAll: true, timeoutMs: 180000,
    source: 'backend/inventory-service/src/test/java/com/projecthk5/inventoryservice/',
    command: gradle('inventory-service', 'test', ['*InventoryOperationServiceTest', '*InventoryRequestValidationTest']) },
  { id: 'CART-ORDER', module: 'Cart/Checkout', title: 'Cart và Checkout domain rules', expected: 'Cart/Checkout unit assertions đạt', safeAll: true, timeoutMs: 180000,
    source: 'backend/cart-service/src/test/java/com/projecthk5/cartservice/service/CartServiceTest.java',
    command: gradle('cart-service', 'test', ['*CartServiceTest']) },
  { id: 'FRONTEND', module: 'Frontend/Security', title: 'React DOM, validation và UI unit tests', expected: 'Node TAP tests hiện có đạt; không phải live E2E', safeAll: true, timeoutMs: 180000,
    source: 'frontend/src/**/*.test.mjs', requiresFrontendDeps: true, command: nodeTests },
  { id: 'QA-UI', module: 'QA Tool', title: 'Playwright dashboard và screenshot thực tế', expected: 'Dashboard hoạt động và mỗi Playwright test có screenshot', safeAll: false, timeoutMs: 120000,
    source: 'tools/tv5-qa-tester/ui/dashboard.spec.mjs', command: { executable: 'node', cwd: 'tools/tv5-qa-tester', args: ['node_modules/@playwright/test/cli.js', 'test', '--reporter=line'], evidence: 'playwright' } },
  { id: 'QA-UI-DIAGNOSTIC', module: 'QA Tool', title: 'Chẩn đoán ảnh FAIL có chủ đích', expected: 'Một assertion chẩn đoán thất bại; runner phải ghi FAIL và chụp ảnh thật', safeAll: false, timeoutMs: 30000,
    source: 'tools/tv5-qa-tester/ui/dashboard.spec.mjs', command: { executable: 'node', cwd: 'tools/tv5-qa-tester', args: ['node_modules/@playwright/test/cli.js', 'test', '--grep', 'diagnostic', '--reporter=line'], evidence: 'playwright', envExtra: { SOOPI_QA_VERIFY_FAILURE: '1' } } },
  { id: 'LIVE-SECURITY', module: 'Security', title: 'HTTP JWT, revoke, XSS, rate limit và Store isolation', expected: 'Chỉ chạy trên stack giả cô lập sau owner gate', safeAll: false, disabledReason: 'Chưa có harness E2E tách khỏi .env.demo chung; không chạy active security scan.', source: 'frontend/e2e/access.spec.ts' },
  { id: 'UNKNOWN', module: 'Order/Inventory', title: 'UNKNOWN claim-conflict/lost-response', expected: 'Cần runner, tuple và receipt được owner duyệt', safeAll: false, disabledReason: 'BLOCKED_OWNER: hai fixture UNKNOWN chưa được ký; không có executable allowlist.', source: 'PR #170 / Issue #18' },
  { id: 'FINANCE-PRODUCTION', module: 'F16 Finance', title: 'Finance/Return production acceptance', expected: 'Cần business approval và live Store/COD source', safeAll: false, disabledReason: 'BLOCKED_OWNER: Finance/Return production policy chưa được ký.', source: 'Issue #12 / #155' }
]);

export const byId = new Map(suites.map(suite => [suite.id, suite]));
export const safeIds = suites.filter(suite => suite.safeAll).map(suite => suite.id);
export function publicSuite(suite) {
  const { command, ...publicFields } = suite;
  return { ...publicFields, commandLabel: command ? [command.executable, ...command.args].join(' ') : null };
}
