import { defineConfig } from '@hey-api/openapi-ts'

// Hey API generates a typed fetch client + models from the backend's OpenAPI
// contract. Output lives in src/api/generated and is gitignored — `pnpm
// gen:api` regenerates it (also runs implicitly as predev / prebuild).
export default defineConfig({
  input: '../backend/src/main/resources/openapi/openapi.yaml',
  output: 'src/api/generated',
  plugins: ['@hey-api/client-fetch'],
})
