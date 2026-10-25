# TalentTriage 🎯

[![CI](https://github.com/mlk/TalentTriage/actions/workflows/ci.yml/badge.svg)](https://github.com/mlk/TalentTriage/actions/workflows/ci.yml)

> **A resource-efficient, local-first recruiter email triage engine built in Kotlin for macOS.**

**TalentTriage** automatically checks your Gmail inbox, filters out recruiter noise using local heuristic rules and lightweight LLM classification, extracts attached Job Descriptions (PDF/Word), and performs deep criteria analysis (Pay, JD relevance, Days in Office) using AirLLM or local Ollama instances.

---

## 🏗️ Architecture Overview

The system operates as an ephemeral, scheduled CLI job (`talent-triage process-inbox`) triggered by macOS `launchd`. It communicates with the Gmail REST API to track state using nested Gmail labels (`Recruiters/*`), avoiding background process memory overhead.

```mermaid
flowchart TD
    subgraph Trigger ["⏰ System Trigger"]
        launchd["macOS launchd (Hourly)"]
    end

    subgraph CLI ["⚡ TalentTriage CLI Entry Point"]
        Command["process-inbox command"]
    end

    subgraph GmailAPI ["📧 Gmail REST API Layer"]
        Fetch["Fetch Unlabelled Emails\n(is:inbox -label:Recruiters/*)"]
        ApplyLabel["Apply Gmail State Labels"]
        Archive["Archive Email (Trash)"]
    end

    subgraph Stage1 ["🔍 Stage 1: Screening & Filtering"]
        Heuristics{"Heuristic Pre-Filter\n(Keywords/Regex)"}
        LocalLLM{"Local LLM Classifier\n(Ollama 1B-3B)"}
    end

    subgraph Stage2 ["📄 Stage 2: Attachment Extraction"]
        PDFParser["Apache PDFBox\n(.pdf Parser)"]
        WordParser["Apache POI\n(.docx Parser)"]
    end

    subgraph Stage3 ["🧠 Stage 3: Deep Criteria Analysis"]
        AirLLM["AirLLM / Ollama 70B\n(Extended Timeout)"]
    end

    %% Execution Flow
    launchd --> Command
    Command --> Fetch
    Fetch --> Heuristics

    %% Stage 1 Branching
    Heuristics -- "No Match" --> ApplyFilteredOut["Label: Recruiters/FilteredOut\n(Stay in Inbox)"]
    Heuristics -- "Candidate Recruiter" --> LocalLLM

    LocalLLM -- "Not Recruiter" --> ApplyFilteredOut
    LocalLLM -- "Recruiter Confirmed" --> Stage2

    %% Stage 2 to Stage 3
    Stage2 --> PDFParser
    Stage2 --> WordParser
    PDFParser --> AirLLM
    WordParser --> AirLLM

    %% Stage 3 Branching
    AirLLM -- "Pay & JD & Office Match" --> LabelFollowUp["Label: Recruiters/WorthFollowUp"]
    AirLLM -- "JD Match, Pay Missing" --> LabelCheckPay["Label: Recruiters/NeedsPayCheck"]
    AirLLM -- "Mismatch / Unsuitable" --> LabelTrash["Label: Recruiters/Trash"]

    %% Gmail Label Updates
    ApplyFilteredOut --> ApplyLabel
    LabelFollowUp --> ApplyLabel
    LabelCheckPay --> ApplyLabel
    LabelTrash --> ApplyLabel
    LabelTrash --> Archive
```

---

## ✨ Key Features

* **Zero-Idle Memory Footprint:** Executes completely as a CLI command and terminates, returning 100% of VRAM/RAM back to macOS.
* **Dual-Tier LLM Architecture:** Uses a fast, small model via Ollama for initial screening and routes qualified emails to an AirLLM Python wrapper (or large quantized Ollama model) for deep evaluation.
* **Stateless Gmail Tracking:** Uses nested Gmail labels (`Recruiters/FilteredOut`, `Recruiters/WorthFollowUp`, `Recruiters/NeedsPayCheck`, `Recruiters/Trash`) as state flags.
* **Document Parsing Engine:** Automatically extracts plain text/Markdown from attached `.pdf` and `.docx` job descriptions using Apache PDFBox and Apache POI. A broken attachment is reported, it never stops the run.
* **OpenAI/Ollama Compatibility:** Leverages native Ollama REST API JSON formats across all endpoints.

---

## 🛠️ Tech Stack & Tooling

* **Language:** Kotlin (JVM 17)
* **Build System:** Gradle with **Kotlin DSL (`build.gradle.kts`)** and the Gradle Wrapper
* **Package Namespace:** `uk.me.michaellloydlee.recruiter`
* **HTTP Client:** Ktor Client CIO (`kotlinx.serialization`)
* **Attachment Parsers:** Apache PDFBox & Apache POI
* **Static Analysis & Linting:** `ktlint`, `detekt`
* **Code Coverage:** JaCoCo (**80% line coverage limit enforced**)
* **CI/CD:** GitHub Actions

---

## 🚀 Getting Started

### Prerequisites

* JDK 17+ (the Gradle daemon is pinned to JDK 17 via `gradle/gradle-daemon-jvm.properties`)
* [Ollama](https://ollama.ai/) installed and running locally on `http://localhost:11434` _(required from the Ollama client story onwards)_
* Python 3.10+ if using the optional AirLLM FastAPI wrapper on `http://localhost:8000` _(optional)_
* Google Cloud OAuth2 credentials (`credentials.json`) for Gmail REST API access — see [Gmail authentication](#-gmail-authentication)

### Installation & Build

1. **Clone the repository:**
   ```bash
   git clone https://github.com/mlk/TalentTriage.git
   cd TalentTriage
   ```

2. **Run the quality gates** (compiles, tests, lints, analyses and enforces the coverage limit):
   ```bash
   ./gradlew check
   ```

3. **Generate the coverage report** (HTML at `build/reports/jacoco/test/html/index.html`):
   ```bash
   ./gradlew jacocoTestReport
   ```

4. **Build the executable** (`build/install/recruiter-cli/bin/recruiter-cli`):
   ```bash
   ./gradlew installDist
   ```

---

## ⌨️ Command Line Interface

The executable is `recruiter-cli`. Every operation is a subcommand:

```bash
# build/install/recruiter-cli/bin/recruiter-cli process-inbox
# build/install/recruiter-cli/bin/recruiter-cli authenticate
# build/install/recruiter-cli/bin/recruiter-cli pending
```

or, without installing the distribution:

```bash
./gradlew run --args="process-inbox"
```

`process-inbox` runs one inbox triage pass and writes **structured** log output — one JSON
object per line — to standard output:

```json
{"timestamp":"2026-10-24T07:57:23.916683Z","level":"INFO","event":"process-inbox.started","message":"Inbox triage run started","attributes":{"trigger":"cli"}}
{"timestamp":"2026-10-24T07:57:23.926923Z","level":"INFO","event":"process-inbox.completed","message":"Inbox triage run completed"}
```

The command exits with status code `0` when the run succeeded and `1` when the command line
could not be parsed. The pipeline stages behind the run (fetching the mail, screening it,
extracting the attachments, evaluating them) are delivered by their own stories.

> **Note:** on JDK 24 and newer, Clikt's terminal detection prints a `native-access` warning
> to standard error. Standard output stays clean JSON; add `--enable-native-access=ALL-UNNAMED`
> to the JVM arguments to silence it.

---

## 📬 The state of the mailbox

Gmail is the state store of the pipeline. Every processed message carries one
of the nested state labels, so the search `is:inbox -label:Recruiters/*` matches
exactly the messages that were not processed yet:

| Label | Meaning | Archived |
| --- | --- | --- |
| `Recruiters/FilteredOut` | not a recruiter mail | no, it stays in the inbox |
| `Recruiters/WorthFollowUp` | worth a follow up | no, it stays in the inbox |
| `Recruiters/NeedsPayCheck` | a recruiter mail, but the pay has to be checked | no, it stays in the inbox |
| `Recruiters/Trash` | not worth any attention | yes, the message leaves the inbox |

A message carries exactly one state label - applying a new state removes the
other ones - and the state labels are created in the mailbox when they do not
exist yet.

The pipeline is a scheduled job, so it only processes the mail that arrived
since the last run: the search is `is:inbox -label:Recruiters/* after:<time>`,
the window is 24 hours by default.

`recruiter-cli pending` lists the messages that were not processed yet, it is
the quickest way to check that the credentials work. The pipeline only looks at
the mail that arrived recently — the last 24 hours by default, `--since P7D`
looks a week back:

```bash
recruiter-cli pending            # the last 24 hours
recruiter-cli pending --since P7D  # the last seven days
```

```json
{"timestamp":"…","level":"INFO","event":"pending.listed","message":"The inbox messages that were not processed yet","attributes":{"count":"2"}}
{"timestamp":"…","level":"INFO","event":"pending.message","message":"A message of the inbox that was not processed yet","attributes":{"id":"…","sender":"…","subject":"…"}}
```

---

## 🔐 Gmail authentication

The Gmail REST API needs an OAuth2 token. The token is obtained once and refreshed
automatically afterwards.

1. **Create a Google Cloud project** and enable the Gmail API, then create an *OAuth client
   ID* of the type *Desktop app*. Download the credentials and save them as
   `credentials.json` in the working directory:

   ```json
   {
     "installed": {
       "client_id": "…apps.googleusercontent.com",
       "client_secret": "…",
       "auth_uri": "https://accounts.google.com/o/oauth2/auth",
       "token_uri": "https://oauth2.googleapis.com/token",
       "redirect_uris": ["http://localhost"]
     }
   }
   ```

   Both the `installed`/`web` format of Google and the flat format above are accepted.

2. **Authorise the application:**

   ```bash
   recruiter-cli authenticate
   ```

   The command prints the URL of the Google authorisation page. Open it in a browser, allow
   the application to access the mailbox and copy the authorisation code from the address bar
   of the redirect. The redirect itself fails to connect — that is expected, the code in the
   address bar is what matters.

3. **Exchange the code for a token:**

   ```bash
   recruiter-cli authenticate <code>
   ```

   The token is written to `token.json` (both files are git-ignored) and the access token is
   refreshed automatically when it expires.

---

## 🧪 Quality Standards & Testing

This repository enforces strict code quality gates via Gradle tasks:

| Command | Purpose |
| --- | --- |
| `./gradlew test` | Runs the JUnit 5 test suite |
| `./gradlew ktlintCheck` | Verifies formatting/lint rules (`ktlint_official` code style) |
| `./gradlew ktlintFormat` | Auto-formats the sources |
| `./gradlew detekt` | Runs static analysis (zero warnings allowed) |
| `./gradlew jacocoTestReport` | Writes the JaCoCo HTML/XML report and **fails the build** below 80% line coverage |
| `./gradlew check` | Runs all of the above as a single gate |
| `./gradlew installDist` | Builds the `recruiter-cli` executable in `build/install/recruiter-cli/bin` |

Configuration lives in:

* `gradle/libs.versions.toml` — version catalog (all dependency/plugin versions)
* `config/detekt/detekt.yml` — detekt rule configuration
* `.editorconfig` — ktlint/IDE formatting rules

---

## 📁 Project Layout

```
src/main/kotlin/uk/me/michaellloydlee/recruiter/
├── application/       # Use cases / orchestration
├── cli/               # Command line entry point (Clikt commands)
├── domain/            # Pure domain model, no infrastructure dependencies
└── infrastructure/    # Gmail, Ollama, PDF/Word adapters, logging
    ├── gmail/         # OAuth2 credentials, token store, Gmail REST client, label state
    ├── logging/       # Structured (JSON lines) logger
    ├── parser/        # Text extraction of the attached documents (PDFBox)
    └── serialization/ # Shared kotlinx.serialization helpers
```

> **Note:** the package namespace drops the hyphens of the `uk.me.michael-lloyd-lee` domain because hyphens are not legal in Kotlin package names.

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
