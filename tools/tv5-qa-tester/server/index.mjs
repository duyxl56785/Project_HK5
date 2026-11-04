import { createServer } from './http.mjs';
import { Runner } from './runner.mjs';
const port = 4318;
const runner = new Runner();
await runner.hydrate();
createServer({port,runner}).listen(port, '127.0.0.1', () => console.log(`Soopi TV5 QA Tester: http://127.0.0.1:${port}`));
