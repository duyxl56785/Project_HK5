import { useEffect, useState } from 'react';
import { blockedWorkspaceAdapter } from './workspaceAdapter';
import { WorkspaceView } from './WorkspaceView';
import type { LoadState, Scope, View, WorkspaceAdapter, WorkspaceSnapshot } from './workspaceTypes';

const deniedScope: Scope = { kind: 'denied' };
export interface FinanceWorkspaceProps { adapter?: WorkspaceAdapter; scope?: Scope; initialView?: View }
export function FinanceWorkspace({ adapter = blockedWorkspaceAdapter, scope = deniedScope, initialView }: FinanceWorkspaceProps) {
  const scopeKey = JSON.stringify(scope);
  const [attempt, setAttempt] = useState(0);
  const [loaded, setLoaded] = useState<{ adapter: WorkspaceAdapter; key: string; attempt: number; state: LoadState<WorkspaceSnapshot> } | null>(null);
  useEffect(() => {
    const abort = new AbortController();
    const identity = { adapter, key: scopeKey, attempt };
    if (scope.kind === 'denied') { setLoaded({ ...identity, state: { status: 'denied' } }); return () => abort.abort(); }
    void (async () => adapter.read(scope, abort.signal))().then(state => {
      if (!abort.signal.aborted) setLoaded({ ...identity, state });
    }).catch(() => { if (!abort.signal.aborted) setLoaded({ ...identity, state: { status: 'error' } }); });
    return () => abort.abort();
    // scopeKey represents the complete immutable scope, including its kind and exact Store ID.
  }, [adapter, scopeKey, attempt]);
  // Hide old data synchronously, before effects, on scope/source/retry changes.
  const state: LoadState<WorkspaceSnapshot> = loaded?.adapter === adapter && loaded.key === scopeKey && loaded.attempt === attempt ? loaded.state : { status: 'loading' };
  return <WorkspaceView key={`${adapter.id}:${scopeKey}`} state={state} scope={scope} initialView={initialView} onRetry={() => setAttempt(n => n + 1)} />;
}
