package comp3011.assignment1.controller;

import comp3011.assignment1.model.TranscriptionResponse;
import comp3011.assignment1.model.TranscriptionResult;
import comp3011.assignment1.service.SpeechToTextService;
import comp3011.assignment1.service.TokenUsageStatisticsService;
import comp3011.assignment1.util.AudioFilenames;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * Receives recorded audio from the browser and returns its transcript.
 * Stateless: the only field is the injected service, so one instance
 * can safely serve many concurrent requests.
 */
@RestController
@RequestMapping("/api/v1")
public class TranscriptionController {
    private final SpeechToTextService speechToTextService;
    private final TokenUsageStatisticsService statistics;

    public TranscriptionController(SpeechToTextService speechToText,
                                   TokenUsageStatisticsService statistics) {
        this.speechToTextService = speechToText;
        this.statistics = statistics;
    }

    @PostMapping(path = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionResponse transcribe(@RequestPart("audio") MultipartFile audio) throws IOException {
        if (audio.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio file must not be empty.");
        }
        // Validate before selecting a profile-dependent STT implementation.
        // Otherwise the offline stub would accept a media type that the real
        // provider path rejects, making local regression tests misleading.
        String contentType = AudioFilenames.supportedMimeType(audio.getContentType());
        TranscriptionResult result = speechToTextService.transcribe(audio.getBytes(), contentType);

        // Record usage only on the success path: a failed call throws before
        // reaching this line, so the counters never include work that produced
        // no transcript. This is also why the internal result type carries
        // token counts that the external response does not - the numbers are
        // needed here, but are not part of the transcribe contract.
        statistics.record(result.inputTokens(), result.outputTokens());

        return new TranscriptionResponse(result.text());
    }
}
