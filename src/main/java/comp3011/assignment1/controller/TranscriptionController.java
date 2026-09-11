package comp3011.assignment1.controller;

import comp3011.assignment1.model.TranscriptionResponse;
import comp3011.assignment1.model.TranscriptionResult;
import comp3011.assignment1.service.SpeechToTextService;
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

    public TranscriptionController(SpeechToTextService speechToTextService) {
        this.speechToTextService = speechToTextService;
    }

    @PostMapping(path = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionResponse transcribe(@RequestPart("audio") MultipartFile audio) throws IOException {
        if (audio.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio file must not be empty.");
        }
        TranscriptionResult result = speechToTextService.transcribe(audio.getBytes(), audio.getContentType());
        return new TranscriptionResponse(result.text());
    }
}