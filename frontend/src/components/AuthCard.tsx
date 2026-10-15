import { ApiError } from '@/api/client'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

// Shared layout and form pieces for the pages around logging in (login, sign-up, verify, forgot/reset password).

/** A centered card with a title, optional description, content and footer (e.g. links to other pages). */
export function AuthCard({
  title,
  description,
  children,
  footer,
}: {
  title: string
  description?: React.ReactNode
  children: React.ReactNode
  footer?: React.ReactNode
}) {
  return (
    <main className="flex min-h-svh items-center justify-center p-6">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-2xl">{title}</CardTitle>
          {description && <CardDescription>{description}</CardDescription>}
        </CardHeader>
        <CardContent>{children}</CardContent>
        {footer && <CardFooter className="flex flex-col gap-2 text-sm text-muted-foreground">{footer}</CardFooter>}
      </Card>
    </main>
  )
}

/** A labelled input. Shows the backend's message for this field when the last request had a field error. */
export function Field({
  id,
  label,
  hint,
  error,
  ...input
}: { id: string; label: string; hint?: string; error?: unknown } & React.ComponentProps<typeof Input>) {
  const fieldError = error instanceof ApiError ? error.problem.errors?.find((e) => e.field === id)?.message : undefined
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} name={id} aria-invalid={fieldError ? true : undefined} {...input} />
      {fieldError ? (
        <p className="text-sm text-destructive">{fieldError}</p>
      ) : (
        hint && <p className="text-xs text-muted-foreground">{hint}</p>
      )}
    </div>
  )
}

/** An error (red) or success (muted) message under a form. */
export function FormMessage({ error, children }: { error?: boolean; children?: React.ReactNode }) {
  if (!children) return null
  return (
    <p
      role={error ? 'alert' : 'status'}
      className={error ? 'text-sm text-destructive' : 'text-sm text-muted-foreground'}
    >
      {children}
    </p>
  )
}
