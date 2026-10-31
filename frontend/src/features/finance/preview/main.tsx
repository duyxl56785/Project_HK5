import { useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { FinanceWorkspace } from '../FinanceWorkspace';
import { createPreviewAdapter } from '../fixtures';
import type { Scope, View } from '../workspaceTypes';
import './preview.css';

function Preview() {
  const params = new URLSearchParams(window.location.search);
  const [kind, setKind] = useState(params.get('scope') ?? 'admin');
  const [mode, setMode] = useState(params.get('state') ?? 'ready');
  const scope: Scope = kind === 'admin' ? { kind: 'admin' } : ['store-a', 'store-b'].includes(kind) ? { kind: 'store', storeId: kind } : { kind: 'denied' };
  const adapter = useMemo(() => createPreviewAdapter(mode), [mode]);
  const initialView = ['finance', 'cod', 'settlement', 'qa', 'prs'].includes(params.get('view') ?? '') ? params.get('view') as View : 'finance';
  return <><div className="tv5-preview-controls"><strong>TV5 FEATURE PREVIEW</strong><span>Phạm vi mô phỏng, không phải đăng nhập hay phân quyền thật.</span>
    <label>Preview scope<select aria-label="Preview scope" value={kind} onChange={e => setKind(e.target.value)}><option value="admin">Admin demo</option><option value="store-a">Store A demo</option><option value="store-b">Store B demo</option><option value="denied">Không có quyền</option></select></label>
    <label>Preview state<select aria-label="Preview state" value={mode} onChange={e => setMode(e.target.value)}>{['ready', 'loading', 'empty', 'error', 'denied', 'blocked'].map(v => <option key={v}>{v}</option>)}</select></label>
  </div><FinanceWorkspace adapter={adapter} scope={scope} initialView={initialView} /></>;
}
createRoot(document.getElementById('root')!).render(<Preview />);
