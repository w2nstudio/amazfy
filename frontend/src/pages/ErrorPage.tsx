import { isRouteErrorResponse, Link, useRouteError } from 'react-router'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { Button } from '@/components/ui/button'

// Shown instead of a page that crashed (an error while rendering or loading it), see errorElement in src/router.tsx.
// Without it React Router shows its developer screen with the technical error and stack trace to every visitor.
// The details go to the browser console only; the visitor gets a plain message and a way on.
export function ErrorPage({ insideLayout = false }: { insideLayout?: boolean }) {
  const error = useRouteError()
  if (isRouteErrorResponse(error) && error.status === 404) return <NotFoundPage />
  console.error(error)

  return (
    <main
      role="alert"
      className={
        insideLayout
          ? 'flex flex-col items-start gap-4'
          : 'flex min-h-svh flex-col items-center justify-center gap-4 p-6 text-center'
      }
    >
      <h2 className="text-2xl font-semibold">Something went wrong</h2>
      <p className="text-sm text-muted-foreground">
        This page ran into a problem. Reloading usually helps; if it keeps happening, please let us know.
      </p>
      <div className="flex gap-2">
        <Button onClick={() => window.location.reload()}>Reload</Button>
        <Button asChild variant="outline">
          <Link to="/">Go to home</Link>
        </Button>
      </div>
    </main>
  )
}
