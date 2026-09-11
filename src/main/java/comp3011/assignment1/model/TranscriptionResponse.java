package comp3011.assignment1.model;

/**
 * JSON body returned by POST /api/v1/transcribe on success, e.g. {"text": "hello"}.
 * The YAML spec does not cover this endpoint, so this shape is our own contract
 * with the front end. Token counts stay internal (see TranscriptionResult).
 */
public record TranscriptionResponse(String text) {
}