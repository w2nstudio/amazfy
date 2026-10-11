import { createBrowserRouter } from 'react-router'
import { HomePage } from '@/pages/HomePage'
import { NotFoundPage } from '@/pages/NotFoundPage'

// All pages and their URLs. New pages (login, products, dashboard, ...) are added here.
export const router = createBrowserRouter([
  { path: '/', element: <HomePage /> },
  { path: '*', element: <NotFoundPage /> },
])
