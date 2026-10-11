import { useGetSystemInfo } from '@/api/generated/system/system'
import { Button } from '@/components/ui/button'

// Placeholder home page. Shows the backend's version to prove frontend ↔ backend works end to end.
export function HomePage() {
  const systemInfo = useGetSystemInfo()

  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-4 p-6">
      <h1 className="text-4xl font-semibold tracking-tight">Amazfy</h1>
      <p className="text-muted-foreground">External traffic, measured in Amazon sales.</p>
      <Button>Get started</Button>

      <p className="text-muted-foreground text-sm">
        {systemInfo.isPending && 'Connecting to backend…'}
        {systemInfo.isError && 'Backend not reachable'}
        {systemInfo.isSuccess && `Backend: ${systemInfo.data.data.name} ${systemInfo.data.data.version}`}
      </p>
    </main>
  )
}
