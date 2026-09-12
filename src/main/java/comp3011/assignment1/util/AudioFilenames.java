package comp3011.assignment1.util;

import comp3011.assignment1.exception.SpeechToTextException;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Derives the filename sent to the Cloud STT provider.
 *
 * <p>The provider determines the audio container from the {@code filename}
 * extension in the multipart body, not from the part's Content-Type header.
 * Browser MediaRecorder blobs arrive with an empty or generic original
 * filename ("blob"), so the filename is reconstructed from the MIME type the
 * browser reported.
 *
 * <p>Unknown or missing types are rejected rather than guessed: the front end
 * negotiates its container against this same list, so anything outside it did
 * not come from our client and deserves a fast local rejection instead of a
 * remote round trip that fails obscurely.
 */
public final class AudioFilenames {

    private static final String BASE_NAME = "recording";

    private static final Map<String, String> EXTENSIONS = Map.of(
            "audio/webm", "webm",
            "audio/ogg", "ogg",
            "audio/mp4", "mp4",
            "audio/mpeg", "mp3",
            "audio/wav", "wav",
            "audio/x-wav", "wav",
            "audio/flac", "flac",
            "audio/x-flac", "flac");

    private AudioFilenames() {
        // Utility class: never instantiated.
    }

    /**
     * Normalises and validates a browser-supplied audio media type. Keeping
     * this before the service boundary makes the stub and Cloud profiles obey
     * the same public API contract.
     */
    public static String supportedMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new SpeechToTextException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Audio part is missing a Content-Type.");
        }

        String mimeType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(mimeType);
        if (extension == null) {
            throw new SpeechToTextException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported audio type: " + mimeType);
        }
        return mimeType;
    }

    public static String filenameFor(String contentType) {
        String mimeType = supportedMimeType(contentType);
        return BASE_NAME + "." + EXTENSIONS.get(mimeType);
    }
}
