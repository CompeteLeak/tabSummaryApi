# tabSummaryApi

**Created:** 2026-04-05

Spring Boot REST API powering the Tab & Research Summarizer Chrome extension. Accepts raw page text, summarizes it via the Anthropic Claude API, and enforces a free/paid usage tier model with Lemon Squeezy subscription handling.

---

## Architecture

```
Chrome Extension
      │
      │  POST /api/summarize  (X-Api-Key header, optional)
      ▼
┌─────────────────────────────────────────────────────┐
│                  Spring Boot API                     │
│                                                     │
│  ApiKeyAuthFilter  ──►  SummarizeController         │
│                               │                     │
│                         SummarizeService            │
│                          ├── Rate limit check       │
│                          ├── Claude API call        │
│                          └── Usage recording        │
│                                                     │
│  WebhookController  ──►  WebhookService             │
│                          └── UserService            │
│                                                     │
│  AccountController  ──►  UsageRepository            │
└─────────────┬───────────────────────────────────────┘
              │
      PostgreSQL (Railway)
```


---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17 (LTS) |
| Framework | Spring Boot | 3.4.1 |
| ORM | Spring Data JPA / Hibernate | (managed by Spring Boot) |
| Security | Spring Security | (managed by Spring Boot) |
| Database (prod) | PostgreSQL | Railway-provisioned |
| Database (local) | H2 in-memory | (managed by Spring Boot) |
| AI / LLM | Anthropic Claude | claude-opus-4-6 |
| Payments | Lemon Squeezy | webhook-based |
| Hosting | Railway | — |
| Build tool | Maven | 3.13+ |
| CI/CD | GitHub Actions | — |

---

## API Endpoints

### `POST /api/summarize`
Summarizes the content of a web page.

**Headers:**
- `X-Api-Key: tsk_...` — optional for free tier, required for paid

**Body:**
```json
{
  "url": "https://example.com/article",
  "content": "raw visible text from the page"
}
```

**Response:**
```json
{
  "title": "Article title",
  "summary": "2-3 sentence summary.",
  "keyPoints": ["Point 1", "Point 2", "Point 3"]
}
```

**Error responses:**
- `429` — free tier monthly limit reached
- `401` — API key provided but invalid
- `400` — missing required fields

---

### `GET /api/account/status`
Returns the current user's tier and usage.

**Headers:** `X-Api-Key: tsk_...` (required)

**Response:**
```json
{
  "tier": "free",
  "usageCount": 12,
  "limit": 20
}
```

---

### `POST /api/webhook/lemon-squeezy`
Receives payment events from Lemon Squeezy. Verified by HMAC-SHA256 signature.

**Headers:** `X-Signature: <hmac>`

---

## Project Structure

```
src/main/java/com/competeleak/summarizer/
├── SummarizerApplication.java
├── config/
│   ├── LlmConfig.java          — Claude RestClient bean
│   └── SecurityConfig.java     — stateless filter chain, CORS
├── controller/
│   ├── SummarizeController.java
│   ├── WebhookController.java
│   └── AccountController.java
├── exception/
│   ├── RateLimitExceededException.java
│   └── GlobalExceptionHandler.java
├── filter/
│   └── ApiKeyAuthFilter.java   — reads X-Api-Key, attaches User to request
├── model/
│   ├── User.java               — FREE / PAID tier, apiKey, email
│   └── UsageRecord.java        — per-request log with billingMonth key
├── repository/
│   ├── UserRepository.java
│   └── UsageRepository.java
└── service/
    ├── SummarizeService.java   — rate limit → Claude call → usage record
    ├── UserService.java        — create / upgrade / downgrade users
    └── WebhookService.java     — HMAC verify + Lemon Squeezy event handling
```

---

## Running Locally

**Prerequisites:**
- Java 17
- Maven 3.8+
- An Anthropic API key

**1. Clone the repo**
```bash
git clone https://github.com/CompeteLeak/tabSummaryApi.git
cd tabSummaryApi
```

**2. Set environment variables**

No PostgreSQL needed locally — the `local` profile uses an H2 in-memory database.

Create a `.env` file or export these in your shell:
```bash
export LLM_API_KEY=your_anthropic_api_key
export LEMON_SQUEEZY_WEBHOOK_SECRET=dev-secret
```

**3. Run with the local profile**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The API will be available at `http://localhost:8080`.

H2 console (for inspecting the in-memory DB) at `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:summarizer`
- Username: `sa` / Password: *(blank)*

**4. Test the summarize endpoint**
```bash
curl -X POST http://localhost:8080/api/summarize \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com","content":"Your page text here"}'
```

---

## CI/CD Pipeline

- Every push and pull request runs `mvn clean verify` via GitHub Actions
- Railway auto-deploys whenever a commit lands on `main`
- The `main` branch is protected — CI must pass before any push or PR merge is allowed
- Combined effect: Railway only ever deploys code that has passed the build and tests

No GitHub secrets required for CI — Railway watches the repo directly.

---

## Environment Variables (Production — Railway)

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://...` from Railway PostgreSQL service |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password |
| `LLM_API_KEY` | Anthropic API key |
| `LEMON_SQUEEZY_WEBHOOK_SECRET` | From Lemon Squeezy webhook settings |
