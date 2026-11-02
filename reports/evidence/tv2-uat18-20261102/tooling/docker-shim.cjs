// Local Windows-only preload, not committed: the specs hardcode a unix Docker socket that Docker Desktop on Windows lacks.
const cp = require('node:child_process');
const { syncBuiltinESMExports } = require('node:module');
const original = cp.execFileSync;
cp.execFileSync = (file, args, options) => original(file,
  file === 'docker' ? args.filter((arg) => arg !== '--host=unix:///var/run/docker.sock') : args, options);
syncBuiltinESMExports();
