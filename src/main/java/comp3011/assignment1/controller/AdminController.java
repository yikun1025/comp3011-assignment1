package comp3011.assignment1.controller;

import comp3011.assignment1.model.UptimeResponse;
import comp3011.assignment1.service.UptimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UptimeService uptimeService;

    public AdminController(UptimeService uptimeService) {
        this.uptimeService = uptimeService;
    }

    @GetMapping("/uptime")
    public UptimeResponse getUptime() {
        return uptimeService.getUptime();
    }
}