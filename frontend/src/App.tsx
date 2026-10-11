import { Button } from '@/components/ui/button'

// Placeholder page; routing, layout and data loading come in the next steps.
function App() {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-4 p-6">
      <h1 className="text-4xl font-semibold tracking-tight">Amazfy</h1>
      <p className="text-muted-foreground">External traffic, measured in Amazon sales.</p>
      <Button>Get started</Button>
    </main>
  )
}

export default App
