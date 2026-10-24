# Code Review Guidelines

You are reviewing a pull request for `TalentTriage`, a local-first recruiter email triage engine written in Kotlin for macOS. Before reviewing, read `AGENTS.md` to understand the project's architecture, tooling, quality gates, and environment quirks.

## Review Process

1. **Read the full diff** — examine every file changed, not just the summary.
2. **Read `AGENTS.md`** — understand the project's architecture, tooling, quality gates, and environment quirks before forming opinions.
3. **Check against project conventions** — verify the PR follows the rules in `AGENTS.md` (ktlint `ktlint_official` style, detekt with zero tolerated issues, JaCoCo line coverage ≥ 80%, `allWarningsAsErrors`, package/layer boundaries, no network in tests).
4. **Trace the code flow** — for new features, follow the data flow from CLI → application → domain → infrastructure (e.g. `process-inbox` → Gmail fetcher → heuristic pre-filter → local LLM classifier → attachment extraction → deep evaluation). For fixes, verify the root cause is addressed.
5. **Evaluate test coverage** — check that new code has tests, edge cases are covered, and doubles/fakes are appropriate instead of live network calls.

## Kotlin-Specific Checks

* **Null safety** — no `!!` unless the value was just checked; prefer `?.`, `?:`, `requireNotNull` with a message, or an early return.
* **Exhaustiveness** — `when` over a sealed class/enum stays exhaustive; new enum values must be handled in every `when` that switches on it.
* **Coroutines** — no `runBlocking` in production code (CLI entry point aside), no `GlobalScope`; blocking IO (PDFBox, POI, file access) belongs in `withContext(Dispatchers.IO)`; every launched coroutine has a structured scope.
* **Resource handling** — `HttpClient` and file streams are closed (`use { }`, `closeable`); no leaked clients or open files in long-running loops.
* **Error handling** — exceptions are not swallowed; either rethrown, translated into a domain-level error, or logged with context. `catch (e: Exception)` around a whole block without rethrow is a smell.
* **Immutability & state** — `val` over `var`; `data class` for value objects; no mutable collections shared between layers.
* **Serialization** — `@Serializable` models stay in the infrastructure layer, with `kotlinx.serialization` used for the Ollama/Gmail JSON payloads.
* **Layering** — `domain` must not depend on `infrastructure` or `cli`; `application` orchestrates, `infrastructure` talks to the outside world.
* **Magic values** — magic numbers/strings are extracted into named constants.

## Review Format

Structure your review as a PR comment with these sections:

### Review

Start with one sentence summarizing what the PR does and your overall impression.

### Strengths

List 3–10 numbered strengths with **bold titles** and detailed explanations. For each strength, explain _why_ it's good, referencing specific code patterns, design decisions, or test strategies. Be specific — name functions, classes, or patterns.

Examples of what to praise:
* Clean architecture decisions (domain stays free of Ktor/IO, adapters behind interfaces)
* Proper error handling and graceful degradation (a failed Gmail call must not abort the whole run)
* Coroutine correctness (structured concurrency, dispatcher choice, no leaked scopes)
* Test quality (realistic fakes for the Gmail/Ollama clients, coverage of error paths, no live network)
* Gmail label state machine correctness (`Recruiters/FilteredOut`, `Recruiters/WorthFollowUp`, `Recruiters/NeedsPayCheck`, `Recruiters/Trash`)
* Prompt/LLM call hygiene (timeouts, retry limits, no token blow-up)
* Documentation quality (KDoc, README updates, `AGENTS.md` notes)

### Suggestions (not blockers)

List specific, actionable suggestions. For each:
* Give a **bold title** that names the code element (function, file, pattern).
* Explain the concern with code-level detail.
* Suggest a concrete fix or alternative.
* Clearly state why it's not a blocker (e.g., "the current approach is pragmatic," "this is a future concern," "the test covers the expected path").

Focus on:
* Future-proofing (heuristics that might break, hardcoded values, model names, timeouts)
* Edge cases (`null` handling, error paths, boundary conditions, empty inbox, oversized attachments)
* Documentation gaps (behavioral differences, conventions not noted)
* Performance considerations (sequential vs parallel Gmail calls, attachment size limits)
* Consistency with the rest of the codebase (naming, error types, logging)

### Verdict

End with a clear verdict:
* **Ready to merge** if no blockers.
* **Fix before merging** if there are issues that should be addressed. List them explicitly.

Include one sentence summarizing why, referencing the PR's main contribution.

## What Makes a Good Review

* **Be specific** — reference file paths, function names, and line-level patterns.
* **Be proportional** — a small fix PR gets a short review; a large feature PR gets thorough analysis.
* **Distinguish priority** — clearly separate "fix before merge" from "future enhancement."
* **Acknowledge shortcuts** — the project uses intentional shortcuts (e.g. single-tier LLM before the AirLLM engine lands, no streaming). Note them without demanding they be fixed now.
* **Check test quality** — verify tests use appropriate fakes, cover edge cases, and don't just test the happy path.
* **Reference `AGENTS.md`** — when suggesting something, check if `AGENTS.md` already addresses it. If it does, note that the PR follows convention. If not, suggest adding it.

## Things to Skip

* Don't demand changes the project's issue tracker already accounts for as future work.
* Don't flag style issues that ktlint/detekt would catch in CI.
* Don't suggest adding tests for trivial getters or simple pass-through functions.
* Don't require streaming, tool calls, or perfect JSON fidelity beyond what the current story asks for — those are tracked as separate issues.
* Don't ask for large model downloads or live LLM/Gmail calls in tests — the test suite must stay offline and cheap.
