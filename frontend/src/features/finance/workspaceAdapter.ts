import type { Scope, WorkspaceAdapter } from './workspaceTypes';

// Safe default until a real, owner-reviewed read contract and current authority exist.
export const blockedWorkspaceAdapter: WorkspaceAdapter = {
  id: 'api-not-integrated',
  async read(scope: Scope) { return { status: scope.kind === 'denied' ? 'denied' : 'blocked' }; },
};
