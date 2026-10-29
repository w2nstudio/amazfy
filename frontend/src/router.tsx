import { createBrowserRouter } from 'react-router'
import { RequireAuth } from '@/auth/RequireAuth'
import { AppLayout } from '@/components/AppLayout'
import { BrandsPage } from '@/pages/BrandsPage'
import { CreateAccountPage } from '@/pages/CreateAccountPage'
import { ErrorPage } from '@/pages/ErrorPage'
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage'
import { HomePage } from '@/pages/HomePage'
import { ImportPage } from '@/pages/ImportPage'
import { LoginPage } from '@/pages/LoginPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { ProductsPage } from '@/pages/ProductsPage'
import { ResetPasswordPage } from '@/pages/ResetPasswordPage'
import { SignUpPage } from '@/pages/SignUpPage'
import { VerifyEmailPage } from '@/pages/VerifyEmailPage'

// All pages and their URLs. New pages (products, dashboard, ...) are added here.
// Pages inside the RequireAuth route need a logged-in user; the others are public.
// A page that crashes shows ErrorPage: inside the header for app pages, as a full page for all others.
export const router = createBrowserRouter([
  {
    errorElement: <ErrorPage />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignUpPage /> },
      // Opened from links in emails the backend sends (see SmtpAuthEmailSender)
      { path: '/verify-email', element: <VerifyEmailPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
      { path: '/reset-password', element: <ResetPasswordPage /> },
      {
        element: <RequireAuth />,
        children: [
          {
            // Header with account switcher and page links around these pages
            element: <AppLayout />,
            children: [
              {
                // A crash in one page keeps the header, so the user can switch account or go elsewhere
                errorElement: <ErrorPage insideLayout />,
                children: [
                  { path: '/', element: <HomePage /> },
                  { path: '/brands', element: <BrandsPage /> },
                  { path: '/products', element: <ProductsPage /> },
                  { path: '/import', element: <ImportPage /> },
                ],
              },
            ],
          },
          { path: '/onboarding', element: <CreateAccountPage firstAccount /> },
          { path: '/accounts/new', element: <CreateAccountPage firstAccount={false} /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
