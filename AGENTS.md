# AGENTS.md

Guidance for AI agents (and humans) working in this repository.

## Project

`TalentTriage` is a local-first recruiter email triage engine written in Kotlin for macOS. It
polls a Gmail inbox, filters recruiter noise with local heuristic rules and a small local LLM
classifier, extracts the attached Job Descriptions (`.pdf` / `.docx`) and evaluates them against
the criteria (pay, JD relevance, days in office). The whole thing is an ephemeral CLI job — it
starts, does its work and exits.

| Part | Choice |
| --- | --- |
| Language | Kotlin (JVM 17) |
| Build | Gradle, Kotlin DSL, the Gradle wrapper |
| Package namespace | `uk.me.michaellloydlee.recruiter` |
| HTTP | Ktor client (CIO) with `kotlinx.serialization` |
| Documents | Apache PDFBox (`.pdf`), Apache POI (`.docx`) |
| Quality | ktlint, detekt, JaCoCo (80% line coverage enforced), GitHub Actions |

## Architecture

```
src/main/kotlin/uk/me/michaellloydlee/recruiter/
├── application/       # Use cases / orchestration
├── cli/               # Command line entry point (Clikt commands)
├── domain/            # Pure model and rules, no infrastructure dependencies
└── infrastructure/    # Gmail, Ollama, document parsing, logging
```

* `domain` is pure Kotlin: no Ktor, no file system, no clock. It must not import from
  `infrastructure` or `cli`.
* `application` orchestrates the use cases.
* `infrastructure` implements the outbound adapters: the Gmail REST API, the Ollama / AirLLM
  HTTP API, the document parsers.
* `cli` parses the command line, wires the use cases and maps the failures to exit codes.

## Conventions

### Interfaces are the exception, not the rule

**Do not add an interface because a class might be replaced later.** The default is a concrete
class; an interface has to earn its place. Every interface is a promise to maintain, a layer of
indirection and a second name for the same thing.

Add an interface only when it is **absolutely required**, that means when at least one of these
is true:

* **There is more than one implementation and the caller must not know about it.**
  `DocumentTextExtractor` is the example: the PDF and the Word parser implement it and the
  attachment processor picks the implementation by the type of the attachment.
* **The dependency has to be inverted**, because the implementation is not available to the
  caller (a different module, a third party library, a code generator).
* **The contract is part of a public API** that is meant to be implemented outside of this
  repository.

Do not add an interface for any of these reasons:

* to make a class "testable" — a test can use the real class with a fake collaborator, or a
  mocking framework;
* to have "one abstraction per class";
* because a second implementation is planned — add the interface when the second implementation
  exists, not before.

The same applies to the other abstractions: prefer a data class over a class hierarchy, a sealed
interface over a base class with a flag, and a function over an object with one method.

### Errors are values, not exceptions

A failure that the pipeline can recover from is returned, not thrown. A broken attachment is a
`ExtractionResult.Failed`, a broken token is a `GmailAuthenticationException` that the command
turns into an exit code. Throwing is for the cases the caller cannot do anything about.

### Everything is injected

The clock, the HTTP client, the sinks and the parsers are constructor parameters with a default.
That is what makes the code testable without a network and without a file system.

## Quality gates

All of them run in CI, and they fail the build:

| Command | Purpose |
| --- | --- |
| `./gradlew check` | Tests, ktlint, detekt and the coverage verification |
| `./gradlew test` | The JUnit 5 test suite |
| `./gradlew ktlintCheck` / `ktlintFormat` | Formatting, `ktlint_official` style |
| `./gradlew detekt` | Static analysis, zero tolerated findings |
| `./gradlew jacocoTestReport` | The coverage report, fails below 80% line coverage |

* **ktlint** — the code style is `ktlint_official`, configured in `.editorconfig`.
* **detekt** — the rules are configured in `config/detekt/detekt.yml` (`maxIssues: 0`,
  `warningsAsErrors: true`). Fix the finding, do not widen the rule set.
* **JaCoCo** — the build enforces **80% line coverage**. `jacocoTestReport` is finalized by the
  verification task, so the report command itself fails below the limit.
* **Kotlin** — `allWarningsAsErrors` is on: a warning is a build failure.

## Testing conventions

* Tests live in `src/test/kotlin/` and mirror the production package path.
* `kotlin.test` on top of the JUnit 5 platform: `kotlin.test.Test`, `assertEquals`, `assertTrue`.
* Test names are camelCase function names (`fun handlesEmptyInbox()`), not backticked strings.
* Prefer real collaborators over mocks: a PDF is built with PDFBox, an HTTP call goes through the
  Ktor `MockEngine`, a file goes into a temporary directory.
* A test must not depend on the machine it runs on — no file of the working directory, no
  network, no clock of the host.

### Do not test the libraries

A test has to show what **this project** does, not what a library does. The behaviour of Ktor,
PDFBox, POI or Clikt belongs to the library and is covered by its own tests.

* Test the request the client builds, the text the extractor returns, the label the state manager
  applies — the parts this project owns.
* Do not assert what a library does with the values it was given: that a request times out
  because the timeout plugin was installed, that a payload is encoded the way the serializer
  encodes it, that the help output of the command line parser looks like something.
* Testing that the project *uses* a library correctly is fine: that the client refuses a client
  without the `HttpTimeout` plugin is a rule of this project, the timeout itself is not.

A test that would break when the library is upgraded, without a change in this repository, is a
test of the library — delete it.

## Environment quirks

* **The Gradle daemon is pinned to JDK 17** (`gradle/gradle-daemon-jvm.properties`,
  `toolchainVersion=17`) and the Kotlin toolchain is `jvmToolchain(17)`. detekt 1.23.x embeds a
  Kotlin compiler that cannot analyse on JDK 24+ and crashes with a bare `26.0.1` error when the
  daemon runs on a newer JDK.
* **JDK 17 has to be installed locally.** There is no toolchain resolver in
  `settings.gradle.kts`, so Gradle cannot download it.
* **The package namespace drops the hyphens** of the `uk.me.michael-lloyd-lee` domain: hyphens
  are not legal in a Kotlin package name. The Gradle `group` id still uses the hyphenated form.
* **The token file holds a secret.** `credentials.json` and `token.json` are git-ignored and must
  never be committed.
* **The Ollama API streams by default.** The `stream` field of a chat request has no default on
  purpose: a field with a default is left out of the payload when the encoder does not write the
  defaults, and the API then answers with a stream of JSON lines that the client cannot read.
* **The test suite must stay offline** — no Gmail, no Ollama, no model downloads.
