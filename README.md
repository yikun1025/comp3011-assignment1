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
| `POST` | `/api/v1/transcribe` | Accepts `multipart/form-data`, returns the transcript | Stub complete, real service pending |
| `GET` | `/api/v1/global/stats` | Cumulative input and output token counts | Not implemented |
| `POST` | `/api/v1/admin/shutdown` | Requests a graceful shutdown | Not implemented |

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
up. It is annotated `@ActiveProfiles("stub")` because the real speech-to-text
implementation does not exist yet, so under the default profile there is no
`SpeechToTextService` bean and the context genuinely cannot start.

Once the real implementation exists, a separate test will cover the default
profile, asserting that the production wiring is correct.

---

## Not yet written

The sections below are placeholders for work still to be done. Each needs the
reasoning filled in, not just a description of what the code does.

### Calling the real speech-to-text service

- Why the read timeout is bounded, and how its value relates to the five-second
  response requirement
- How upstream failures map onto status codes returned to the client, and why a
  timeout becomes 504 rather than 502
- Why the audio filename extension is derived from the MIME type
- What the call log records, and what it deliberately does not

### Browser recording

- The state machine, and why all visual changes go through one function
- Compression settings and the resulting size of a thirty-second recording
- Container format negotiation across browsers
- Why the client timeout must exceed the server read timeout

### Statistics and graceful shutdown

- Why the token counters use an atomic type
- Why "shut down only once" needs a single atomic operation, not a check
  followed by a write
- Why the graceful shutdown window exceeds the upstream read timeout

### Concurrency

- Which concurrency model was chosen and why the alternatives were rejected
- Which races exist and how each is prevented
- What the load test asserts, and why asserting peak concurrency matters

### Keeping the API key out of everything

- Why scrubbing happens in the logging pipeline rather than at each call site
- The path by which an exception can leak past an exception handler

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

More entries to follow as the remaining stages are completed.