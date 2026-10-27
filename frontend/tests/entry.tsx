import React from 'react';
import { createRoot } from 'react-dom/client';
import { SharedRouter } from '../src/app/SharedRouter';
import { createApiClient } from '../src/shared/apiClient';
import '../src/styles.css';

// Test-only host. No fixture client or shared router is imported by production main.
const client = createApiClient();
Object.assign(window, { testClient: client });
createRoot(document.getElementById('root')!).render(<React.StrictMode><SharedRouter client={client} /></React.StrictMode>);
