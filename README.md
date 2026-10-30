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
        Fetch["Fetch Unlabelled Emails\n(is:inbox, every state label excluded)"]
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
    Heuristics -- "No Match" --> Report["Reported, no label\n(the window is the guard)"]
    Heuristics -- "Candidate Recruiter" --> LocalLLM

    LocalLLM -- "Not Recruiter" --> Report
    LocalLLM -- "Recruiter Confirmed" --> Stage2

    %% Stage 2 to Stage 3
    Stage2 --> PDFParser
    Stage2 --> WordParser
    PDFParser --> AirLLM
    WordParser --> AirLLM

    %% Stage 3 Branching
    AirLLM -- "Pay & JD & Office Match" --> LabelFollowUp["Label: Recruiters/WorthFollowUp"]
    AirLLM -- "JD Match, Pay Or Office Days Missing" --> LabelMoreInfo["Label: Recruiters/NeedsMoreInformation"]
    AirLLM -- "Mismatch / Unsuitable" --> LabelTrash["Label: Recruiters/Trash"]

    %% Gmail Label Updates
    LabelFollowUp --> ApplyLabel
    LabelMoreInfo --> ApplyLabel
    LabelTrash --> ApplyLabel
    LabelTrash --> Archive
```

---

## ✨ Key Features

* **Zero-Idle Memory Footprint:** Executes completely as a CLI command and terminates, returning 100% of VRAM/RAM back to macOS.
* **Dual-Tier LLM Architecture:** Uses a fast, small model via Ollama for initial screening and routes qualified emails to an AirLLM Python wrapper (or large quantized Ollama model) for deep evaluation.
* **Stateless Gmail Tracking:** Uses nested Gmail labels (`Recruiters/JobListing`, `Recruiters/WorthFollowUp`, `Recruiters/NeedsMoreInformation`, `Recruiters/Trash`) as state flags.
* **Document Parsing Engine:** Automatically extracts plain text/Markdown from attached `.pdf` and `.docx` job descriptions using Apache PDFBox and Apache POI. A broken attachment is reported, it never stops the run.
* **OpenAI/Ollama Compatibility:** Leverages native Ollama REST API JSON formats across all endpoints.
* **Dual-Tier LLM Architecture:** The same typed client talks to the local Ollama instance (short
  timeout) and to the AirLLM wrapper (long timeout) — only the base URL and the timeout differ.

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

5. **Build the standalone jar** (`build/libs/recruiter-cli.jar`):
   ```bash
   ./gradlew shadowJar
   ```
   The jar carries the dependencies with it, it runs with nothing but a JVM:
   ```bash
   java -jar build/libs/recruiter-cli.jar process-inbox
   ```

---

## ⌨️ Command Line Interface

The executable is `recruiter-cli`. Every operation is a subcommand:

```bash
# build/install/recruiter-cli/bin/recruiter-cli process-inbox
# build/install/recruiter-cli/bin/recruiter-cli authenticate
# build/install/recruiter-cli/bin/recruiter-cli pending
# build/install/recruiter-cli/bin/recruiter-cli requirements cv.pdf
```

`process-inbox` fetches the messages of the inbox that were not processed yet
and screens them: the ones that are not recruiter mails are reported and stay
unlabelled - the window of the run is what keeps the next run from looking at
them again -, the job listings get the label `Recruiters/JobListing`, the
recruiter mails are counted for the deep evaluation that comes with its own
story.

The requirements of the job seeker — the pay, the profile and the number of
days of office a job may demand — are read from `requirements.json` in the
working directory (`--requirements` changes the file):

```json
{
  "minimum_pay": { "amount": 120000, "currency": "EUR", "period": "year" },
  "maximum_office_days": 2,
  "profile": "Senior Kotlin engineer, distributed systems, 8+ years, fintech"
}
```

The `requirements` command writes that file from the CV of the job seeker, see
[the requirements of the job seeker](#-the-requirements-of-the-job-seeker).

The screening asks a model of the local Ollama instance. By default it is a
**decision model** (`clef:cloud`), which answers a typed question on the
decision API of Ollama: `from a recruiter`, `not from a recruiter` or
`unknown`, with the probability of every answer. A chat model can be used
instead:

```bash
# the screening of the recruiter mails
recruiter-cli process-inbox                                  # the decision model (default)
recruiter-cli process-inbox --recruiter-verification=llm     # a chat model
recruiter-cli process-inbox --recruiter-verification-model=nimble

# the evaluation of the job descriptions
recruiter-cli process-inbox --job-description-evaluation=decision   # the decision model (default)
recruiter-cli process-inbox --job-description-evaluation=chat      # a chat model
recruiter-cli process-inbox --job-description-evaluation-model=clef:cloud
```

Every step of the pipeline gets its own model, so the model of the recruiter
verification is chosen with `--recruiter-verification-model` and the model of
the job description evaluation with `--job-description-evaluation-model`.

A job listing - an alert of a job board, it offers many jobs at once and it is
not one job - is told apart before the model is asked: the rules of the sender
and of the subject know the boards. `jobalerts-noreply@linkedin.com` sends the
alerts of LinkedIn, `inmail-hit-reply@linkedin.com` sends the mails of the
people of LinkedIn, so the rules catch the listing and not the recruiter mail.
A message the rules do not catch is asked about once more, whether it is a job
listing: the second question only refines the state of a message that is not a
recruiter mail, a model that is not sure about it filters the message out. A
listing is not evaluated - it is not one job - and stays in the inbox.

The default models of both verifiers run in the cloud of Ollama (`clef:cloud`
and `minimax-m3.1-flash:cloud`), they need a paid plan or usage credits. A
local model works as well: `nimble`, `tev1`, `clef` and `clef-flash` are the
decision models, they need Ollama v0.35.0 or later.

### The arguments of the commands

| Command | Argument | What it does | Default |
| --- | --- | --- | --- |
| `authenticate` | `--credentials`, `-c` | The file with the OAuth2 client credentials | `credentials.json` |
| `authenticate` | `--token`, `-t` | The file the OAuth2 token is stored in | `token.json` |
| `pending` | `--credentials`, `-c` | The file with the OAuth2 client credentials | `credentials.json` |
| `pending` | `--token`, `-t` | The file the OAuth2 token is stored in | `token.json` |
| `pending` | `--since` | How far back the messages are looked for, an ISO-8601 duration | the gap since the last successful run |
| `pending` | `--limit`, `-l` | The number of messages that are shown | `20` |
| `process-inbox` | `--credentials`, `-c` | The file with the OAuth2 client credentials | `credentials.json` |
| `process-inbox` | `--token`, `-t` | The file the OAuth2 token is stored in | `token.json` |
| `process-inbox` | `--requirements`, `-q` | The file with the requirements of the job seeker | `requirements.json` |
| `process-inbox` | `--since` | How far back the messages are looked for, an ISO-8601 duration | the gap since the last successful run |
| `process-inbox` | `--recruiter-verification` | How the recruiter mails are verified: a decision model or a chat model | `decision` |
| `process-inbox` | `--recruiter-verification-model`, `-m` | The model that verifies the recruiter mails | `clef:cloud` |
| `process-inbox` | `--recruiter-verification-endpoint` | The endpoint of the model that verifies the recruiter mails | `http://localhost:11434` |
| `process-inbox` | `--recruiter-verification-timeout` | How long the verification of a recruiter mail may take, in milliseconds | `120000` |
| `process-inbox` | `--job-description-evaluation` | How the job descriptions are evaluated: a decision model or a chat model | `decision` |
| `process-inbox` | `--job-description-evaluation-model`, `-j` | The model that evaluates the job descriptions | `clef:cloud` |
| `process-inbox` | `--job-description-evaluation-endpoint` | The endpoint of the model that evaluates the job descriptions | `http://localhost:11434` |
| `process-inbox` | `--job-description-evaluation-timeout` | How long the evaluation of a job description may take, in milliseconds | `600000` |
| `requirements` | `CV` | The CV of the job seeker, a PDF or a Word document | — |
| `requirements` | `--requirements`, `-q` | The file the requirements of the job seeker are written to | `requirements.json` |
| `requirements` | `--force`, `-f` | Overwrite the file of the requirements when it exists | off |
| `requirements` | `--max-office-days` | The number of days of office a job may demand | `2` |
| `requirements` | `--model`, `-m` | The chat model that writes the profile of the job seeker | `minimax-m3.1-flash:cloud` |
| `requirements` | `--decision-model`, `-d` | The decision model that names the minimum pay | `clef:cloud` |
| `requirements` | `--endpoint` | The endpoint of the models | `http://localhost:11434` |
| `requirements` | `--timeout` | How long a call to a model may take, in milliseconds | `600000` |

The models with the suffix `:cloud` run in the cloud of Ollama and need a paid plan or usage
credits. The local decision models are `nimble`, `tev1`, `clef` and `clef-flash`, they need
Ollama v0.35.0 or later.

### The requirements of the job seeker

The `requirements` command writes the requirements of the job seeker from the CV of the job
seeker, a PDF or a Word document:

```bash
recruiter-cli requirements cv.pdf                       # writes requirements.json
recruiter-cli requirements cv.docx --requirements my.json --max-office-days 3
```

The command reads the CV with the extractors of the documents, a chat model writes the profile
of the job seeker from it - in the words of the job seeker, so that the same text is used for
the evaluation of the job descriptions - and the decision model of Ollama names the minimum pay
out of the pays of the market for the role of the job seeker. The days of office a job may
demand are not in the CV, the job seeker says them on the command line (`--max-office-days`,
the default is 2).

The pays of the market come from the web: the command looks for the pay of the role in the
region of the job seeker with the web search of Ollama. The search needs an API key of Ollama
in the environment:

```bash
export OLLAMA_API_KEY="…"
```

Without the key the command cannot look at the web, it uses the default pays of the region
instead and says so in the log. The command prints what it suggests and it does not overwrite
an existing file without `--force`:

```
The requirements of the job seeker:
  profile: Senior Kotlin engineer of the payment team, twelve years of experience…
  minimum pay: 83000 GBP per year
  maximum office days: 2
  file: requirements.json
```

The file holds the personal data of the job seeker, it is git-ignored like the credentials and
the token. The suggestion is a suggestion: read it, change what is wrong and keep the file -
the run of the inbox reads it as it is.


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

## ⏰ The scheduled run on macOS

The application is an ephemeral job: it starts, it triages the inbox and it
exits. `launchd`, the scheduler of macOS, starts it every hour.

### 1. Install the application

```bash
sudo mkdir -p /usr/local/lib/recruiter-cli/logs
sudo cp build/libs/recruiter-cli.jar /usr/local/lib/recruiter-cli/
sudo cp credentials.json requirements.json /usr/local/lib/recruiter-cli/
sudo chown -R "$USER" /usr/local/lib/recruiter-cli
```

The OAuth2 token is written by the `authenticate` command. It is a step of the
installation, not of the job - the job refreshes the token by itself:

```bash
cd /usr/local/lib/recruiter-cli
java -jar recruiter-cli.jar authenticate
```

The moment of the last successful run is written next to the token
(`token.json.last-run`), the directory has to be writable by the user that runs
the job.

### 2. Install the launchd job

The sample of the job is `config/launchd/com.user.recruiter-checker.plist`. It
runs `java -jar … process-inbox` at the start of every hour, the structured
log of the run goes to `logs/recruiter-cli.out.log` and the errors go to
`logs/recruiter-cli.err.log`.

```bash
cp config/launchd/com.user.recruiter-checker.plist ~/Library/LaunchAgents/
launchctl load ~/Library/LaunchAgents/com.user.recruiter-checker.plist
```

The paths of the jar and of the files in the plist are the ones of the
installation above, change them when the application lives somewhere else.
The job runs once an hour:

```bash
launchctl list | grep recruiter-checker
```

The job does not run when it is loaded, it waits for the next hour. To check
the setup right away, start it by hand:

```bash
launchctl start com.user.recruiter-checker
```

To stop it:

```bash
launchctl unload ~/Library/LaunchAgents/com.user.recruiter-checker.plist
```

The job is a user agent, it runs as the user that installed it. The
`--enable-native-access=ALL-UNNAMED` argument of the plist keeps the error log
free of the warning that Clikt prints on JDK 24 and newer.

The plist only passes the files of the run to the command, everything else
takes the default of the command: the models, their endpoints and their
timeouts. Add the arguments to the `ProgramArguments` array of the plist to
change them:

```xml
<string>--recruiter-verification-model</string>
<string>nimble</string>
```

### 3. The window of a run

The window (`--since`) says how far back a run looks for mail. It is the only
thing that keeps the pipeline away from the mail that was screened out: the
mail that was evaluated carries a label and the search skips it, the mail that
was screened out carries none.

The window of a run is the **gap since the last successful run**: the run
looks back to the moment of that run and no further. The moment is kept next
to the token (`token.json.last-run`) and it is written after a successful run
only — a run that failed looks back further, so the mail of the failed run is
picked up again.

That is why the sample does not pass a window:

* every mail is looked at once, the screened out mail is not looked at again
  and again,
* the mail of an outage is never lost: `launchd` coalesces the runs that were
  missed into one when the machine wakes up, and that run looks back to the
  last run before the sleep,
* the window does not have to be tuned for the machine it runs on.

A window on the command line wins over the gap, it is the way to look at more
mail than the last run:

```bash
recruiter-cli process-inbox --since P7D   # the last seven days
```

---

## 📬 The state of the mailbox

Gmail is the state store of the pipeline. Every message that the pipeline
evaluated carries one of the nested state labels, so the search `is:inbox` with
every state label excluded (`-label:Recruiters/JobListing
-label:Recruiters/WorthFollowUp …`) matches exactly the messages that were not
processed yet. Gmail does not understand a wildcard in the name of a label:
`is:inbox -label:Recruiters/*` still returns the messages that carry a nested
label, so every state label is excluded by its own path:

| Label | Meaning | Archived |
| --- | --- | --- |
| `Recruiters/JobListing` | a job listing, an alert of a job board that offers many jobs at once | no, it stays in the inbox |
| `Recruiters/WorthFollowUp` | worth a follow up | no, it stays in the inbox |
| `Recruiters/NeedsMoreInformation` | a recruiter mail that matches, but something is missing: the pay is not stated, the job description does not say how many days of office it demands or the model could not evaluate the job description | no, it stays in the inbox |
| `Recruiters/Trash` | not worth any attention | yes, the message leaves the inbox |

A message carries exactly one state label - applying a new state removes the
other ones - and the state labels are created in the mailbox when they do not
exist yet. The state label is the marker of the pipeline: the search of the
pending mails (`is:inbox` with every state label excluded) skips every message
that carries one, so a message that was evaluated is never looked at again. A
message that was only screened out keeps no label: it is reported by the run
and the window of the run is what stops the next run from looking at it again.

The pipeline is a scheduled job, so it only processes the mail that arrived
since the last run: the search is `is:inbox` with every state label excluded
and `after:<time>` added. The time is the moment of the last successful run,
so the run looks at every mail exactly once. `--since` says how far back the
run looks instead: `PT24H` or `P1D` are the same, `P7D` looks a week back.
The window of the run is in the log of the run.

`recruiter-cli pending` lists the messages that were not processed yet, it is
the quickest way to check that the credentials work. It takes the same window
as the run, so both commands look at the same mails:

```bash
recruiter-cli pending              # the mail since the last run
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
| `./gradlew shadowJar` | Builds the standalone `build/libs/recruiter-cli.jar` |

Configuration lives in:

* `gradle/libs.versions.toml` — version catalog (all dependency/plugin versions)
* `config/detekt/detekt.yml` — detekt rule configuration
* `config/launchd/com.user.recruiter-checker.plist` — the sample of the macOS launchd job
* `.editorconfig` — ktlint/IDE formatting rules

The conventions of the repository (architecture, the rules for interfaces and
error handling, the testing conventions) are documented in
[`AGENTS.md`](AGENTS.md).

---

## 📁 Project Layout

```
src/main/kotlin/uk/me/michaellloydlee/recruiter/
├── application/       # Use cases / orchestration
│   ├── evaluation/    # The evaluation of the job descriptions
│   ├── requirements/  # The requirements of the job seeker, written from the CV
│   └── screening/     # The heuristic pre-filter and the local classifier
│                      # (the pipeline of the process-inbox command)
├── cli/               # Command line entry point (Clikt commands)
├── domain/            # Pure domain model, no infrastructure dependencies
│   ├── gmail/         # The state labels of the pipeline
│   ├── requirements/  # The requirements of the job seeker and the pays of the market
│   └── screening/     # The rules of the screening
└── infrastructure/    # Gmail, Ollama, PDF/Word adapters, logging
    ├── gmail/         # OAuth2 credentials, token store, Gmail REST client, label state
    ├── llm/           # Ollama chat client, decision client and web search
    ├── logging/       # Structured (JSON lines) logger
    ├── parser/        # Text extraction of the attached documents (PDFBox)
    ├── requirements/  # The file of the requirements of the job seeker
    ├── run/           # The moment of the last successful run
    └── serialization/ # Shared kotlinx.serialization helpers
config/
├── detekt/            # The rules of the static analysis
└── launchd/           # The sample of the macOS launchd job
```

> **Note:** the package namespace drops the hyphens of the `uk.me.michael-lloyd-lee` domain because hyphens are not legal in Kotlin package names.

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
