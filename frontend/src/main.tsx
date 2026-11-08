import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './App';
import { readSearchTarget } from './lib/location';

const queryClient = new QueryClient();
const root = document.getElementById('root');
if (root === null) {
  throw new Error('#root 가 없어요');
}

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App target={readSearchTarget(window.location.search)} />
    </QueryClientProvider>
  </StrictMode>,
);
