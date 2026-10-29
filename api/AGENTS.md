# api/AGENTS.md

Spring Boot API in Kotlin (Java 25, Gradle 9). The rules in the [root AGENTS.md](../AGENTS.md) apply too.

## Commands

Run them from `api/`:

```bash
./gradlew build             # compile, ktlint check, tests, JaCoCo coverage gates, jar checks
./gradlew test              # tests only (writes the JaCoCo HTML and XML reports)
./gradlew spotlessApply     # format Kotlin and Gradle files with ktlint
./gradlew generateOpenApi   # write build/openapi/bicimad.json without the EMT
./gradlew bootRun           # run on localhost:8080 (needs .env.local)
./gradlew smokeTest         # call the real EMT with .env.local (manual, never in CI)
./gradlew dockerImageTest   # build the Dockerfile and check the container (needs Docker)
```

## Structure

- `shared/emt`: authentication, resilient HTTP client, cache and quota for the EMT.
- `shared/web`: web configuration shared by the modules.
- `modules/bicimad`: one Spring Modulith module, split into `domain`, `application` (use cases), `ports`, `adapters` and `http`.
- Modules only depend on `shared` and on each other through their public API. Spring Modulith checks the boundaries in the tests.

## Rules

- At least 80 % line coverage in `shared` and in each module; `./gradlew build` fails below it.
- The controllers are the source of the OpenAPI contract. A change to a public endpoint changes the published client, so it is a breaking change (`feat(api)!` with `BREAKING CHANGE:` in the body) unless it only adds.
- springdoc and Swagger UI are for development only: `verifyNoSpringdocInJar` fails the build if they reach the jar.
- Credentials live in `api/.env.local`, which is ignored by git. Never print, log or commit them.
- Tests never call the real EMT: they use WireMock and fixtures. Only `smokeTest` does, and only when a human runs it.
