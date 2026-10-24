# 🦥 SlothScout

A local agent that reads your job-alert emails at 3 a.m., scores every posting against your résumé with a local open-weight model, and greets you in the morning with two lines: *apply to these, ignore the rest.*

Nothing leaves your machine except the IMAP fetch. Your résumé and inbox never touch anyone's API.

## How it works

1. **Schedule**: a Spring `@Scheduled` job runs at 03:00 `America/Sao_Paulo`.
2. **Collect**: reads only the alerts folder over IMAP, read-only. Processed emails are tracked by `Message-ID` in a local H2 DB, so your Gmail "read" state is never touched.
3. **Extract**: one Jsoup parser per sender (LinkedIn, Indeed, Gupy). Each one anchors on the job link and its ID, which stay stable while the email layouts keep changing.
4. **Dedupe**: postings are keyed by `source:jobId`, so the same job from three alerts is scored once.
5. **Score**: one call per job to Gemma via Ollama, using Spring AI structured output (JSON schema enforced by Ollama, mapped to a record). Reasons must quote the posting, and the page marks each quote ✓ if it really appears in the text.
6. **Summarize**: a second call writes the two-line summary from the shortlist. If it leaves out a shortlisted company, a deterministic fallback is used instead.
7. **Deliver**: a Thymeleaf page at `http://localhost:8080` plus `reports/YYYY-MM-DD.md`.

Every job is saved as soon as it is scored. If a run dies halfway, the next one picks up where it stopped.

## Setup

Requirements: JDK 25+, [Ollama](https://ollama.com) running with `ollama pull gemma3:4b`.

```bash
cp config/resume.example.md config/resume.md   # paste your résumé (git-ignored)

# Gmail: create an App Password and a label/filter for the alerts (default label: "Vagas")
export SLOTHSCOUT_IMAP_USER=you@gmail.com
export SLOTHSCOUT_IMAP_PASSWORD=your-app-password
export SLOTHSCOUT_IMAP_FOLDER=Vagas

./mvnw spring-boot:run
```

Open http://localhost:8080 and hit **Rodar agora** to run without waiting for 3 a.m.

## Tuning

- **Criteria** (levels, cities, stacks, exclusions, cutoff) live in `application.yml` under `slothscout.criteria`, not in the prompt.
- **Model**: change one line (`spring.ai.ollama.chat.model`) or set `SLOTHSCOUT_MODEL=qwen2.5:3b`.
- **Parsers**: set `slothscout.run.dump-html=true` to save raw alert HTML into `data/samples/`. Point `slothscout.run.replay-dir` at that folder to reprocess the alerts offline, with no IMAP. This is also handy for comparing models and recording demos.

## Tests

```bash
./mvnw test
```
