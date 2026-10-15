import { createBrowserRouter } from 'react-router'
import { RequireAuth } from '@/auth/RequireAuth'
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage'
import { HomePage } from '@/pages/HomePage'
import { LoginPage } from '@/pages/LoginPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { ResetPasswordPage } from '@/pages/ResetPasswordPage'
import { SignUpPage } from '@/pages/SignUpPage'
import { VerifyEmailPage } from '@/pages/VerifyEmailPage'

// All pages and their URLs. New pages (products, dashboard, ...) are added here.
// Pages inside the RequireAuth route need a logged-in user; the others are public.
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignUpPage /> },
  // Opened from links in emails the backend sends (see SmtpAuthEmailSender)
  { path: '/verify-email', element: <VerifyEmailPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  {
    element: <RequireAuth />,
    children: [{ path: '/', element: <HomePage /> }],
  },
  { path: '*', element: <NotFoundPage /> },
])
