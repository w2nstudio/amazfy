import { Button } from '@/components/ui/button'

// "Continue with Google": a plain link, not an API call. The browser itself goes to the backend, then to Google,
// and comes back logged in (see docs/guides/google-login-implementation.md). New users land on /onboarding.
export function GoogleButton() {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3 text-xs text-muted-foreground">
        <span className="h-px flex-1 bg-border" />
        or
        <span className="h-px flex-1 bg-border" />
      </div>
      <Button variant="outline" asChild>
        <a href="/api/v1/auth/google">Continue with Google</a>
      </Button>
    </div>
  )
}
