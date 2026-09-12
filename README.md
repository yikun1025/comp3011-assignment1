# COMP3011 Assignment 1 — Speech-to-Text Web Service

A Spring Boot application that records audio in the browser, forwards it to a
cloud speech-to-text service, and returns the transcript. Also exposes admin
and statistics endpoints.

This document explains **why** the application is built the way it is. For how
to run it, see [Running](#running) at the end.

---

## Endpoints

| Method | Path | Purpose | Status |
|---|---|---|---|
| `GET` | `/` | Single page with the recording controls | Done |
| `GET` | `/api/v1/admin/uptime` | Server start time and uptime in seconds | Done |
| `POST` | `/api/v1/transcribe` | Accepts `multipart/form-data`, returns the transcript | Done |
| `GET` | `/api/v1/global/stats` | Cumulative input and output token counts | Done |
| `POST` | `/api/v1/admin/shutdown` | Requests a graceful shutdown | Done |

---

## Design decisions

### An injected `Clock` instead of `Instant.now()`

`UptimeService` and `GlobalExceptionHandler` both need the current time.
Calling `Instant.now()` directly would make their behaviour untestable: there
is no way to assert that an uptime calculation is *correct*, only that it is
greater than zero.

A `Clock` bean is defined in `ClockConfig` and injected wherever time is
needed. `UptimeServiceTest` substitutes a mutable clock, advances it by a known
amount, and asserts the reported uptime equals that amount exactly.

The uptime is computed as `Duration.toNanos() / 1_000_000_000.0` rather than
`Duration.getSeconds()`, which truncates to whole seconds.

### Separate internal and external transcription models

`TranscriptionResult` is what the service layer returns: transcript text plus
input and output token counts. `TranscriptionResponse` is what the HTTP client
receives: text only.

Keeping them separate means the token counts — which the statistics endpoint
needs — travel through the application without leaking into the public API
contract. Changing one does not force a change in the other.

Both are records. Their compact constructors reject null text and negative
token counts, so an invalid result cannot be constructed at all.

### The speech-to-text service is behind an interface

`SpeechToTextService` declares a single method. `TranscriptionController`
depends only on the interface; which implementation gets injected is decided by
the active Spring profile.

- `StubSpeechToTextService` — `@Profile("stub")`, returns fixed text after a
  configurable delay. No network, no API key.
- `OpenAiSpeechToTextService` — `@Profile("!stub")`, calls the real service.

This satisfies the requirement that local and deployed configuration differ by
profile and environment variable rather than by commenting out code. It also
makes the whole test suite runnable offline, and provides the artificial
latency the concurrency tests need to produce genuinely overlapping requests.

The stub is deliberately **not** the default profile: the deployment
environment launches the JAR with no arguments, and must get the real service.

### The service layer takes `byte[]`, not `MultipartFile`

`MultipartFile` is a Spring Web type. Accepting it in the service interface
would make the service layer depend on the web layer for no benefit. The
controller extracts the bytes and content type and passes those instead.

### The controller is stateless

Spring creates one controller instance shared by every request thread. Its only
field is a `final` reference to the service, so there is no mutable state to
race on.

### Every error path returns the same shape

`ErrorResponse` has the five fields the specification requires: `timestamp`,
`status`, `error`, `message`, `path`.

`GlobalExceptionHandler` is a `@RestControllerAdvice` that maps exceptions onto
that shape. Framework-level failures are included, not just application ones —
without explicit handlers, a 404 or a 405 would return Spring's default error
body and a machine client would see two different error formats from the same
API.

| Situation | Exception | Response |
|---|---|---|
| Unknown path | `NoResourceFoundException` | 404 |
| Wrong HTTP method | `HttpRequestMethodNotSupportedException` | 405, with `Allow` header |
| Body is not multipart | `HttpMediaTypeNotSupportedException` | 415 |
| Missing `audio` part | `MissingServletRequestPartException` | 400 |
| Empty upload | `ResponseStatusException` from the controller | 400 |
| Upload too large | `MaxUploadSizeExceededException` | 413 |
| Anything else | `Exception` | 500, fixed message, full exception logged |

Three details worth noting:

**The 405 response carries an `Allow` header** because RFC 9110 requires it —
the client is told which methods the path does support. 415 has no equivalent
mandatory header, so the accepted format is stated in the message instead.

**The handler for 415 catches `HttpMediaTypeNotSupportedException`, not its
parent `HttpMediaTypeException`.** The parent would also catch
`HttpMediaTypeNotAcceptableException`, which signals the opposite problem: the
client asked for a response format the server cannot produce. Catching the
parent would label a 406 as a 415 and tell the client to fix a request that was
never wrong.

**406 responses have an empty body, by design.** The error body is JSON, but a
406 means the client has just said it does not accept JSON. There is no format
in which the error could be returned, so the framework returns the status code
and an `Accept` header listing what the server can produce. This is a limit of
content negotiation, not a gap in the error handling.

**The 500 message is a fixed string** and never includes the exception text.
Exception messages can contain internal paths, upstream response bodies, or
credentials. The full exception goes to the log; the client gets a constant.

### The context load test runs under the stub profile

`Assignment1ApplicationTests` starts the whole application and asserts it comes
up. It is annotated `@ActiveProfiles("stub")` so the ordinary test suite never
makes a paid network call or requires a secret.

`ProductionWiringTest` covers the default profile separately. With no API key
available locally, it is the only thing that can catch a wiring or model-name
mistake before submission: it starts the default context with a placeholder
key and asserts what was actually assembled, without making a network call.
The model name is compared verbatim — a `startsWith` check would pass for a
model the specification does not ask for.

---

### Calling the real speech-to-text service

The OpenAI `RestClient` has a five-second connection timeout and a ten-second
read timeout. A request waiting forever for an upstream service is especially
harmful under load: it would keep one request alive indefinitely. The browser
waits fifteen seconds, longer than the server's ten-second upstream window, so
a slow provider produces the useful server-side 504 response instead of an
unexplained browser cancellation.

The service deliberately translates provider failures into stable API errors:
429 becomes 503 (the application cannot serve another transcription right
now), 413 stays 413, other upstream error statuses become 502, and a transport
timeout becomes 504. A missing response body is also treated as 502 rather
than as a successful empty transcript.

The browser supplies a MIME type, but OpenAI also needs a plausible filename
extension when parsing multipart data. `AudioFilenames` maps supported media
types to a safe server-chosen filename; it never trusts a browser filename.

Each STT log entry contains only the byte count, generated filename, model,
elapsed time and token counts. It intentionally omits the bearer token,
Authorization header, audio bytes and transcript text.

### Browser recording

The browser has three states: `idle`, `recording` and `transcribing`.
`setState` is the only place that changes button availability, recording
indicator, timer and status text. This prevents combinations such as an enabled
Start button while recording or a timer that keeps running while uploading.

The recorder requests mono 16 kHz audio at 24 kbps, with echo cancellation and
noise suppression. Speech at 24 kbps is about 3 KB/s, so a thirty-second clip
is roughly 90 KB. This reduces upload time while retaining intelligible speech.

The page negotiates a container before constructing `MediaRecorder`: it prefers
WebM/Opus where available and falls back through WebM, MP4 and Ogg. This avoids
assuming Chrome's format on Safari. `async`/`await`, `AbortController`, and a
single error-display path make microphone, network, timeout and API failures
visible to the user before the controls return to `idle`.

### Statistics and graceful shutdown

Successful transcription results carry input and output token counts internally
to `TokenUsageStatisticsService`. Its `LongAdder` counters avoid lost updates
when many request threads finish together; failed calls do not reach the record
operation and therefore do not inflate the totals.

`ShutdownService` uses `AtomicBoolean.compareAndSet(false, true)` so testing
and setting the shutdown flag happen as one operation. A separate `if` followed
by an assignment could let two simultaneous callers both begin shutdown. The
winning request is answered first; a separate non-daemon thread closes the
Spring context after a short delay. Spring's 15-second graceful-shutdown phase
is longer than the ten-second upstream read timeout, allowing an in-flight STT
call enough time to finish before the process exits.

### Concurrency

The controller retains ordinary blocking code because waiting for an external
HTTP response is easier to read and reason about than a callback chain. Spring
Boot's virtual-thread support lets many such waits overlap without consuming a
large platform-thread pool. The controller itself has no per-request mutable
fields; shared state is limited to the atomic token totals and shutdown flag.

`ConcurrentLoadTest` sends 250 real multipart HTTP requests to an embedded
Tomcat server at one gate. The stub blocks each request for 300 ms and records
the peak simultaneous calls. The test requires every response to be 200, peak
overlap above 200, and completion far faster than serial execution.
`StatisticsRaceConditionTest` releases 400 virtual threads together against
the shared controller and requires the exact expected token-total increase. It
also releases 64 shutdown contenders and proves that exactly one wins. These
tests would expose a plain `long += value` counter or an `if (!flag)` shutdown
implementation.

### Keeping the API key out of everything

`OPENAI_API_KEY` is read at process startup through the environment-backed
configuration property, never from browser JavaScript or a committed file. The
stub profile does not create the OpenAI configuration at all, so offline tests
need no placeholder secret. `OpenAiProperties` overrides its generated
`toString()` to replace the key with `***`; this matters because configuration
binding errors can otherwise log the complete configuration object. No API
endpoint returns provider configuration, and the explicit STT logs avoid secret
or audio content fields.

---

## Running

Requires JDK 21 or later.

```
mvn clean package
java -jar target/stt-app.jar
```

The application listens on port 8080. The API key is read at runtime from the
`OPENAI_API_KEY` environment variable and is never written to logs, responses,
or source control.

To run against the stub service with no network access or API key:

```
java -jar target/stt-app.jar --spring.profiles.active=stub
```

---

## Testing

```
mvn test
```

| Test | What it proves |
|---|---|
| `UptimeServiceTest` | Uptime is computed exactly, against a controlled clock |
| `Assignment1ApplicationTests` | The application context starts under the stub profile |
| `ProductionWiringTest` | The default profile assembles the real service, with the exact model name the specification requires |
| `StubWiringTest` | The stub profile needs no API key — `OpenAiProperties` is not created at all |
| `AdminAndStatsControllerTest` | Uptime, statistics and graceful-shutdown controller contracts are stable |
| `ConcurrentLoadTest` | More than 200 real simultaneous blocking HTTP requests succeed without serial delay |
| `StatisticsRaceConditionTest` | Token statistics retain every update and only one concurrent shutdown caller wins |
