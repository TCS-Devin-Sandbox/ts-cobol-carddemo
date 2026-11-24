package com.carddemo.systemtime;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CDRD (CODATE01) as REST: the MQ request was ignored, so the endpoint takes no input. */
@RestController
@RequestMapping("/api/system-time")
public class SystemTimeController {
    private final SystemTimeService service;

    SystemTimeController(SystemTimeService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> get() {
        var now = service.now();
        return Map.of("tranId", "CDRD", "program", "CODATE01",
                "date", now.date(), "time", now.time(), "reply", now.reply());
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String reply() {
        return service.now().reply();
    }
}
