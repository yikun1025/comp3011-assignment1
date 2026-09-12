package comp3011.assignment1.controller;

import comp3011.assignment1.model.ShutdownResponse;
import comp3011.assignment1.model.UptimeResponse;
import comp3011.assignment1.service.ShutdownService;
import comp3011.assignment1.service.UptimeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server lifecycle endpoints: how long the process has been up, and a request
 * to stop it.
 *
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UptimeService uptimeService;
    private final ShutdownService shutdownService;

    // One constructor only. Spring picks the sole constructor automatically, and
    // every field is final, so the bean cannot exist half-wired.
    public AdminController(UptimeService uptimeService, ShutdownService shutdownService) {
        this.uptimeService = uptimeService;
        this.shutdownService = shutdownService;
    }

    @GetMapping("/uptime")
    public UptimeResponse getUptime() {
        return uptimeService.getUptime();
    }

    /**
     * 202 rather than 200: the response is written before the server has
     * actually stopped, so the spec's "accepted" semantics are the honest ones.
     * Waiting for the shutdown to finish would close the connector mid-response
     * and the caller would see a dropped connection instead of a status code.
     *
     * A second call throws ShutdownInProgressException, which GlobalExceptionHandler
     * turns into the 409 the spec describes. Nothing is decided here.
     */
    @PostMapping(path = "/shutdown", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ShutdownResponse shutdown() {
        shutdownService.requestShutdown();
        return new ShutdownResponse("Graceful shutdown requested.");
    }
}