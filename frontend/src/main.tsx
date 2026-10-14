import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import './index.css'
import { ApiError, refreshAccessToken } from '@/api/client'
import { router } from '@/router'

// One shared cache for all API calls made through the generated TanStack Query hooks
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Retry once on network or server trouble; a 4xx answer (not logged in, no access, ...) won't change by retrying
      retry: (failureCount, error) => failureCount < 1 && !(error instanceof ApiError && error.status < 500),
    },
  },
})

// The access token lives only in memory, so a page reload starts without one. Before showing any page, try to get
// a new one with the refresh cookie: a logged-in user stays logged in across reloads and new tabs.
void refreshAccessToken().finally(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </StrictMode>,
  )
})
