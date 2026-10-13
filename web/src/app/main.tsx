import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './styles/index.css';

async function enableMocking(): Promise<void> {
  if (import.meta.env.MODE !== 'mock') {
    return;
  }
  const { worker } = await import('@/shared/api/mocks/browser');
  await worker.start({ onUnhandledFrame: 'bypass' });
}

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Root element #root not found in index.html');
}

await enableMocking();

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
