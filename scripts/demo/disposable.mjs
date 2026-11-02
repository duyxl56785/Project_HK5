import { randomBytes } from 'node:crypto';

export const newProject = () => `hk5-v7-${randomBytes(4).toString('hex')}`;

/** Destructive rehearsals run only on a stack they create themselves. */
export function requireDisposable(project, existingContainers) {
  if (!/^hk5-v7-[0-9a-f]{8}$/.test(project)) throw new Error(`Refusing ${project}: not a disposable project name`);
  if (existingContainers.length) throw new Error(`Refusing ${project}: it already has containers`);
}
