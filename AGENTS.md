# AGENTS.md

Guidance for AI agents (and humans) working in this repository.

## Project

`TalentTriage` is a resource-efficient, local-first recruiter email triage engine written in
Kotlin for macOS. It polls a Gmail inbox, filters recruiter noise with local heuristic rules
and a small local LLM classifier, extracts attached Job Descriptions (`.pdf` / `.docx`), and
runs a deep criteria evaluation (pay, JD relevance, days in office) against AirLLM or a large
quantized Ollama model. The whole thing is an ephemeral CLI job — it starts, does its work and
exits, leaving no idle process behind.

The GitHub issue tracker is the source of truth for scope. Issues are grouped into
milestones (epics):

| Issue | Story | Milestone |
| --- | --- | --- |
| #1 | 1.1 Kotlin Gradle project, Kotlin DSL, quality tooling | Epic 1: Project Scaffolding, Quality Gates & CLI Skeleton |
| #2 | 1.2 GitHub Actions CI pipeline | Epic 1 |
| #3 | 1.3 Executable CLI entry point & command parser | Epic 1 |
| #4 | 2.1 Gmail OAuth2 credential management & API client | Epic 2: Gmail API Integration & Label State Machine |
| #5 | 2.2 Email fetcher & Gmail label state manager | Epic 2 |
| #6 | 3.1 PDF attachment text extractor | Epic 3: Document Text Extraction Engine |
| #7 | 3.2 Word (`.docx`) attachment text extractor | Epic 3 |
| #8 | 4.1 Internal modular Ollama HTTP client | Epic 4: Heuristic Screening & Dual-Client Ollama Layer |
| #9 | 4.2 Heuristic & local classifier screening service | Epic 4 |
| #10 | 5.1 Deep JD evaluation engine & criteria prompting | Epic 5: In-Depth JD Evaluation via AirLLM Wrapper |
| #11 | 6.1 Gradle Shadow JAR & macOS `launchd` scheduler | Epic 6: Packaging & macOS `launchd` Scheduler |

## Workflow rules (mandatory)

1. **One ticket = one pull request.** Never commit directly to `main`.
2. Branch naming: `feat/issue-<issue-number>-<short-slug>` (e.g. `feat/issue-2-ci-pipeline`).
3. Open the PR with `gh pr create`, referencing the issue (`Closes #<n>`).
4. CI must be green before the PR is considered done.
5. Do not reformat or restructure unrelated code — keep diffs focused on the ticket.
6. **Answer the automated review.** Every PR gets a `review` job (JetBrains
   Junie, see `.junie/guidelines.md`); the job is skipped for fork and
   Dependabot PRs, which have no `JUNIE_API_KEY`. A failed review is re-run
   with *Re-run jobs* in the Actions UI (or `gh run rerun <run-id>`), which
   replays the original pull request payload. After opening the PR, wait for
   the review to finish (`gh pr checks <pr> --watch`), then read the summary
   comment **and** the inline review comments, and deal with **every**
   suggestion:

   * read the summary comment with `gh pr view <pr> --json comments`,
   * read the inline review comments with
     `gh api repos/{owner}/{repo}/pulls/<pr>/comments` (`gh pr view` does not
     return them), and reply to one with
     `gh api repos/{owner}/{repo}/pulls/<pr>/comments/<id>/replies -f body=...`,
   * then implement it (commit on the same branch, push again),
   * ignore it — then say why in a reply to the comment,
   * or, when it is valid but out of scope for this ticket, file a new issue
     (`gh issue create`) or extend an existing one and reference that issue in
     the reply.

   A PR is not done before the reviewer has been answered. Do not leave a
   suggestion unaddressed and unmentioned.

   Every push starts a new review run, and `use_single_comment` only collapses
   the *summary* comment — the inline comments of the previous run stay on the
   diff. Answer them with a short "superseded" reply once the newer run has
   covered the same point; do not implement them twice.

## Tooling

Everything is driven by the Gradle wrapper — never call a system `gradle`.

| Task | Command |
| --- | --- |
| All quality gates (test, ktlint, detekt, coverage) | `./gradlew check` |
| Unit tests only | `./gradlew test` |
| Format check / auto-fix | `./gradlew ktlintCheck` / `./gradlew ktlintFormat` |
| Static analysis | `./gradlew detekt` |
| Coverage report + 80% gate | `./gradlew jacocoTestReport` |
| Clean build output | `./gradlew clean` |
| Build the runnable JAR | `./gradlew build` (shadow JAR lands with issue #11) |

Dependency and plugin versions live in `gradle/libs.versions.toml` — add new
dependencies there, never with a hardcoded version in `build.gradle.kts`.

## Quality gates (all enforced in CI)

* **ktlint** — `ktlint_official` code style, 120 column limit, configured in
  `.editorconfig`. `ktlintCheck` runs over `src/main`, `src/test` **and** the
  Gradle Kotlin scripts, so `build.gradle.kts` is linted too.
* **detekt** — configuration in `config/detekt/detekt.yml`
  (`buildUponDefaultConfig = true`, `warningsAsErrors = true`, `maxIssues = 0`).
  Zero tolerated findings; fix the cause, never widen the rule set to silence it.
* **JaCoCo** — the build enforces a **minimum of 80% line coverage**. The
  `jacocoTestReport` task is finalized by `jacocoTestCoverageVerification`, so
  the report command itself fails the build below the limit. `check` depends on
  the same verification task.
* **Kotlin compiler** — `allWarningsAsErrors = true`: a warning is a build
  failure. No `@Suppress` to silence it unless the suppression is justified in
  a comment.

## Environment quirks

* **The Gradle daemon is pinned to JDK 17** (`gradle/gradle-daemon-jvm.properties`,
  `toolchainVersion=17`) and the Kotlin toolchain is `jvmToolchain(17)`. This is
  not cosmetic: detekt 1.23.x embeds a Kotlin compiler that cannot analyse on
  JDK 24+, and it crashes with a bare `26.0.1` error when the daemon runs on a
  newer JDK. Keep the daemon criteria file in sync with the toolchain version.
* **JDK 17 has to be installed locally.** There is no toolchain resolver
  (`foojay`) in `settings.gradle.kts` and no `toolchainUrl.*` entry in the
  daemon criteria, so Gradle cannot download it: without a local JDK 17 the
  build fails before the first task runs. CI is unaffected — `setup-java`
  installs Temurin 17.
* **The package namespace is `uk.me.michaellloydlee.recruiter`, not
  `uk.me.michael-lloyd-lee.recruiter`.** Hyphens are illegal in Kotlin package
  names — `package uk.me.michael-lloyd-lee.recruiter` is a syntax error. The
  Gradle `group` id still uses the hyphenated form.
* **Local development is macOS**; CI runs on `ubuntu-latest`. Anything
  macOS-specific (`launchd`, the Shadow JAR) must not break the Linux build.
* **Secrets never enter the repository.** `credentials.json`, `token.json` and
  `client_secret.json` are git-ignored; CI uses repository secrets only.
* **The test suite must stay offline.** No test may talk to Gmail, Ollama or
  AirLLM — inject fakes instead (Ktor's `MockEngine` for HTTP, hand-written
  doubles for the label state machine).

## Architecture

```
src/main/kotlin/uk/me/michaellloydlee/recruiter/
├── application/    # use cases / orchestration, no I/O of its own
├── cli/            # command parsing and process exit codes
├── domain/         # pure model + rules, no infrastructure dependencies
└── infrastructure/ # Gmail, Ollama, PDFBox/POI adapters
```

* `domain` is pure Kotlin: no Ktor, no file system, no clock. It must not
  import from `infrastructure` or `cli`.
* `application` orchestrates the use cases and owns the Gmail label state
  transitions (`Recruiters/FilteredOut`, `Recruiters/WorthFollowUp`,
  `Recruiters/NeedsPayCheck`, `Recruiters/Trash`).
* `infrastructure` implements the outbound adapters: Gmail REST API (Ktor
  CIO), Ollama / AirLLM HTTP (Ktor CIO), Apache PDFBox and Apache POI.
* `cli` parses the command line, wires the use cases and maps failures to
  exit codes. The command is `process-inbox`.

### Triage flow (target architecture)

```
launchd (hourly) → CLI process-inbox
  → fetch unlabelled inbox mail (is:inbox -label:Recruiters/*)
  → heuristic pre-filter (keywords/regex)
      ├─ no match      → label Recruiters/FilteredOut, keep in inbox
      └─ candidate     → local LLM classifier (small Ollama model)
                          ├─ not a recruiter → label Recruiters/FilteredOut
                          └─ recruiter      → extract attachments
                                            → PDF / DOCX text extraction
                                            → deep evaluation (AirLLM / large Ollama)
                                                ├─ pay + JD + office match → Recruiters/WorthFollowUp
                                                ├─ JD match, pay unknown   → Recruiters/NeedsPayCheck
                                                └─ mismatch               → Recruiters/Trash (+ archive)
```

## Testing conventions

* Tests live in `src/test/kotlin/` and mirror the production package path.
* Use `kotlin.test` (`kotlin.test.Test`, `assertEquals`, `assertTrue`, …) on
  top of the JUnit 5 platform; the suite runs through `./gradlew test`.
* Test names are camelCase function names (`fun handlesEmptyInbox()`), not
  backticked strings — ktlint's `function-naming` rule only allows the latter
  in a few contexts.
* Every new class needs a test; new code needs coverage of the error paths
  too, not just the happy path.
* HTTP is tested with Ktor's `MockEngine`; the Gmail and Ollama clients take
  their endpoint and credentials as constructor parameters so tests can point
  them at a local fake.
* Prefer fakes over mocks — a small hand-written double is easier to read than
  a mock framework setup.

## Resource usage policy (important)

The maintainer may be on a **metered connection** — never download large
artifacts.

* CI must not pull models, run Ollama, or reach the network beyond Maven
  Central, the Gradle Plugin Portal, the Gradle distribution download
  (`services.gradle.org`, cached by `setup-gradle`) and the GitHub Actions
  cache. The `review` job additionally calls the Junie API.
* Never commit model weights, PDFs/`.docx` fixtures larger than a few KB, or
  anything from `~/Library/LaunchAgents`.
* Tests use tiny in-memory fixtures; a real model run is a manual step.
