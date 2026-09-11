package comp3011.assignment1.service;

import comp3011.assignment1.model.TranscriptionResult;

/**
 * Converts recorded audio into text.
 * The active Spring profile decides which implementation is injected:
 * a stub for offline tests, or the real OpenAI client for local and TITAN runs.
 */
public interface SpeechToTextService {
    TranscriptionResult transcribe(byte[] audio, String contentType);
}