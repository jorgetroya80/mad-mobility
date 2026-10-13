import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './styles/index.css';

async function enableMocking(): Promise<void> {
  if (import.meta.env.MODE !== 'mock') {
    return;
  }
  try {
    const { worker } = await import('@/shared/api/mocks/browser');
    await worker.start({ onUnhandledFrame: 'bypass' });
  } catch (error) {
    // Report it but render anyway: a broken mock setup should not leave a blank page.
    reportError(error);
  }
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
