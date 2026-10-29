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

### The states of a message

Gmail is the state store of the pipeline: a processed message carries exactly one of the four
state labels below `Recruiters/` — `FilteredOut`, `WorthFollowUp`, `NeedsMoreInformation` and
`Trash`. A message without a state label is pending.

* `NeedsMoreInformation` is the single state for a mail that matches but is incomplete. Both gaps
  of a job description land there: the pay is not stated and the days of office are not stated
  (`OfficeDays.UNCLEAR`). It is one state, not two.
* The state is derived from the evaluation, never from the exception of the model.
* The label carries the state, the log carries the reason. A detail that is not a state — why the
  evaluation is incomplete, for instance — belongs in the log.

### The confidence gate of the decision model

A typed answer of the decision model is only used when its confidence is at least `0.5`. The answer
`unclear` is the exception and is never a reason to leave a mail alone. The model reports a low
confidence (0.37) when a job description does not say how many days of office it demands, and that
low confidence is the expected answer, not a defect. Do not remove the exemption and do not raise
the gate.

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

## Workflow

The work is reviewed before it is merged, so it is done in the open.

* **A branch per issue.** `feat/issue-<number>-<slug>` for a story, `fix/issue-<number>-<slug>` for
  a fix, branched off `main`.
* **Small commits.** One commit per step, each of them compiling. The message starts with a
  keyword (`feat:`, `fix:`, `test:`, `docs:`, `refactor:`) and describes the change in one line.
* **Push when the branch is done**, not after every commit. The pull request closes the issue
  (`Closes #<number>`) and lists the acceptance criteria of the issue.
* **CI is the gate.** The `Build & Quality Gates` job has to be green before the review, watched
  with `gh pr checks <number> --watch`.
* **Every comment gets an answer.** A review comment is answered in the thread of the pull
  request, with what was changed or why nothing was changed.
* **Never force-push.** Rebase or merge, do not rewrite the history of a pushed branch.
* **Coverage numbers are measured, not copied.** The percentage in a pull request body is the one
  measured on the branch. When reviewing another branch, measure it again instead of trusting the
  number in the body.

## Issues

A story has to be readable on its own, so a ticket follows a fixed shape:

```markdown
## Epic 5: In-Depth JD Evaluation via AirLLM Wrapper

**Goal:** …

**As the** job seeker,
**I want** …,
**So that** …

## Acceptance Criteria

- [ ] …

---

## Project Context

### Architecture & Technology Stack

* **Language:** Kotlin
* **Build Tool:** Gradle with **Kotlin DSL (`build.gradle.kts`)**
* **Base Package:** `uk.me.michael-lloyd-lee.recruiter`
* **Networking:** Ktor Client (CIO engine, `kotlinx.serialization`)
* **AI Integration:** Ollama REST API client (the decision API `/v1/systemone` and the chat API `/api/chat`, the local Ollama instance and the cloud of Ollama)
* **Quality & CI:** GitHub Actions, JaCoCo (minimum 80% line coverage enforced), `ktlint`, and `detekt`

### Notes

* …

### Definition of Done (DoD)

1. **CI Pipeline:** GitHub Actions workflow passing cleanly on every push/PR.
2. **Test Coverage:** Minimum **80% line coverage** enforced via JaCoCo in the Gradle build.
3. **Static Analysis & Linting:** Zero warnings or errors from `ktlint` and `detekt`.
4. **Architecture:** Clean separation of concerns (Domain, Infrastructure, Application, and CLI entry points).
```

* The title starts with `Story <epic>.<number>:`, the milestone names the epic.
* The acceptance criteria are checkboxes and are checkable: a command, a label or a number.
* A ticket that only describes the wish is not finished. Ask the questions that are open and write
  the answers into the ticket.

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
* **Gmail does not expand a wildcard in a label name.** `-label:Recruiters/*` still returns the
  messages that carry a nested label, and the parent label `Recruiters` does not cover them
  either. Every state label is excluded by its own path, that is the only search that works.
* **The time window is parsed once.** The ISO-8601 duration of `--since` is parsed in
  `domain/gmail/MessageWindow.kt` and shared by `pending` and `process-inbox`; a window of zero or
  less is rejected.
* **The Ollama API streams by default.** The `stream` field of a chat request has no default on
  purpose: a field with a default is left out of the payload when the encoder does not write the
  defaults, and the API then answers with a stream of JSON lines that the client cannot read.
* **The decision API of Ollama is `POST /v1/systemone`.** It answers a typed question about a text
  (`choice`, `noul` or `score`) and gives the probability of every answer. The decision models are
  `nimble`, `tev1`, `clef` and `clef-flash`; `clef:cloud` runs in the cloud of Ollama (Ollama
  v0.40.5 or later, a paid plan or usage credits).
* **The test suite must stay offline** — no Gmail, no Ollama, no model downloads.
