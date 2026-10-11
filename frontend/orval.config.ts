import { defineConfig } from 'orval'

// Generates the typed API client and TanStack Query hooks from the shared contract.
// Output is regenerated before every dev/build run and never committed or edited by hand.
export default defineConfig({
  amazfy: {
    input: '../api/openapi.yaml',
    output: {
      target: 'src/api/generated/amazfy.ts',
      schemas: 'src/api/generated/model',
      mode: 'tags-split', // one file per tag, e.g. system/system.ts
      client: 'react-query',
      httpClient: 'fetch',
      clean: true,
    },
  },
})
