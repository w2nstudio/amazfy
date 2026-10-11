# OpenAPI and contract-first development

How Amazfy defines its REST API in `api/openapi.yaml` and generates code from it. Read this before adding or changing an endpoint.

## What OpenAPI is

OpenAPI is the standard format for describing a REST API: its URLs, HTTP methods, request and response bodies, and errors, written in YAML (or JSON). Most companies with an API publish an OpenAPI description, including Google, Stripe, GitHub and Amazon.

## Two ways to use it

| Style | How it works | Typical use |
| --- | --- | --- |
| **Code-first** | Write controllers; a library (springdoc in Spring) generates the OpenAPI file from the code | Very common in single-team Spring apps; quicker to start |
| **Contract-first** (Amazfy) | Write the OpenAPI file first; generate code from it | Several clients or teams share one API: separate frontend, mobile apps, public APIs |

### Why Amazfy is contract-first

- A separate React frontend, and later possibly mobile apps or partner integrations. One spec generates both the Kotlin server interfaces and the TypeScript client, so they cannot drift apart.
- API design is reviewed **before** code is written, as a small YAML change in a pull request.
- The spec is always-accurate documentation, because the code is forced to match it.

### Trade-offs

- More YAML to write than annotating a controller.
- Generators have quirks, and some OpenAPI features generate awkward code.
- One more tool to learn and keep updated.

### Other API styles you may hear about

- **GraphQL:** clients request exactly the fields they need. Popular for complex, data-heavy frontends.
- **gRPC:** fast binary protocol, contract-first by design (`.proto` files). Mostly service-to-service.
- **tRPC:** shares types directly between a TypeScript backend and frontend. Not applicable to a Kotlin backend.

For a Spring backend with a React frontend, REST + OpenAPI is the most standard choice; contract-first is its more disciplined variant.

## How generation works in Amazfy

```
./gradlew build  (or bootRun, test, compileKotlin)
   └─ openApiGenerate      ← runs first: reads ../api/openapi.yaml
        writes → backend/build/generated/openapi/src/main/kotlin/com/amazfy/api/generated/
                   ├─ SystemApi.kt          (interface, one per tag)
                   ├─ model/SystemInfo.kt   (request/response classes)
                   └─ ApiUtil.kt            (helper)
   └─ compileKotlin        ← compiles our code and the generated code together
```

Three settings in `backend/build.gradle.kts` make this happen:

- `openApiGenerate { ... }`: generator `kotlin-spring`, input file, packages and options.
- `tasks.compileKotlin { dependsOn(tasks.openApiGenerate) }`: generation always runs before compiling.
- `sourceSets.main { kotlin.srcDir(...) }`: the compiler and IntelliJ treat the generated folder as source code.

Generated code lives in `build/`, which Git ignores. It is recreated from the YAML on every build, so **never edit it by hand**: changes would be overwritten.

### Generator options we use

| Option | Effect |
| --- | --- |
| `useSpringBoot4` | Spring Boot 4 / Jakarta imports |
| `interfaceOnly` | Generates interfaces and models only; we write the controllers |
| `skipDefaultInterface` | Interface methods have no default body, so every endpoint must be implemented or compilation fails |
| `useTags` | One interface per tag (`tags: [System]` → `SystemApi`) |
| `useBeanValidation` | Validation annotations from the spec (required, min/max, patterns) |
| `documentationProvider`, `annotationLibrary` = `none` | No Swagger annotations in generated code |
| `exceptionHandler`, `gradleBuildFile` = `false` | We provide our own error handling and build |
| `requestMappingMode` = `none` | Full paths are on each method; no class-level prefix |

## From YAML to code

| In `openapi.yaml` | In the generated code |
| --- | --- |
| `tags: [System]` | interface `SystemApi` |
| `operationId: getSystemInfo` | method `fun getSystemInfo()` |
| path `/api/v1/system/info` + `get:` | `@RequestMapping(method = [GET], value = ["/api/v1/system/info"])` |
| `content: application/json` | `produces = ["application/json"]` |
| response schema `SystemInfo` | return type `ResponseEntity<SystemInfo>` |
| schema `SystemInfo` with `required: [name, version]` | `data class SystemInfo(val name: String, val version: String)`; required fields are non-nullable |

Example: the contract

```yaml
paths:
  /api/v1/system/info:
    get:
      tags: [System]
      operationId: getSystemInfo
      responses:
        "200":
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/SystemInfo"
```

generates

```kotlin
@RestController
interface SystemApi {
    @RequestMapping(method = [RequestMethod.GET], value = ["/api/v1/system/info"], produces = ["application/json"])
    fun getSystemInfo(): ResponseEntity<SystemInfo>   // no body: must be implemented
}
```

and our controller implements it:

```kotlin
@RestController
class SystemController(private val buildProperties: BuildProperties) : SystemApi {
    override fun getSystemInfo(): ResponseEntity<SystemInfo> = ResponseEntity.ok(SystemInfo(...))
}
```

Spring reads the `@RequestMapping` annotations from the interface, so the controller carries no URL annotations: the URL lives only in the contract.

## Adding or changing an endpoint

1. Edit `api/openapi.yaml`: path, method, `operationId`, `tags`, request and response schemas. Paths start with `/api/v1/`.
2. Run `./gradlew openApiGenerate` (or any build) and look at the generated interface.
3. Implement the new method in the controller for that tag (create the controller in the module's `adapter/inbound/web` if it's a new tag).
4. Add a test, then `./gradlew build`.
5. Open a pull request: reviewers see the YAML change (the design) and the implementation together.

If the YAML has an endpoint the controller doesn't implement, the build fails with "Class '…Controller' is not abstract and does not implement abstract member". That is the contract being enforced.

## Frontend: the TypeScript client

The frontend generates its client from the same file with [Orval](https://orval.dev) (`frontend/orval.config.ts`):

- `npm run generate:api` (also automatic before `npm run dev` and `npm run build`) writes `frontend/src/api/generated/`: TypeScript types for every schema (e.g. `SystemInfo`), a fetch function per operation (`getSystemInfo()`) and a TanStack Query hook (`useGetSystemInfo()`).
- Pages call the hooks; the hook handles loading, errors and caching.
- In development, Vite proxies `/api` to the backend on port 8080, so the generated relative URLs just work.

A contract change that breaks the frontend shows up as a TypeScript error in `npm run build`, just as a backend mismatch fails `./gradlew build`.

## IntelliJ tips

- Ctrl+click a generated type (e.g. `SystemApi`) to open the generated file.
- If generated types show in red, run a build once (`./gradlew build` or the Gradle tool window), then **Reload All Gradle Projects**.

## Further reading

- [OpenAPI Specification](https://spec.openapis.org/oas/latest.html)
- [OpenAPI Generator: kotlin-spring options](https://openapi-generator.tech/docs/generators/kotlin-spring)
