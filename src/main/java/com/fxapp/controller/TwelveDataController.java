package com.fxapp.controller;

import com.fxapp.service.TwelveDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TwelveDataController {
    private final TwelveDataService svc;

    // 캔들
    @GetMapping("/candles-td")
    public ResponseEntity<Map<String, Object>> candles(
            @RequestParam String pair,
            @RequestParam String tf,
            @RequestParam(defaultValue = "240") int limit
    ) throws Exception {
        try {
            return ResponseEntity.ok(svc.fetchCandles(pair, tf, limit));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                Map<String, Object> stale = svc.staleOrNull(pair, tf, limit);
                if (stale != null) return ResponseEntity.ok(stale);
                Map<String, Object> daily = svc.fallbackDaily(pair, limit);
                if (daily != null) return ResponseEntity.ok(daily);
                return ResponseEntity.ok(Map.of(
                        "pair", pair, "tf", tf, "source", "TwelveData (empty fallback)",
                        "stale", true, "points", java.util.List.of()
                ));
            }
            throw ex;
        }
    }

    // 현재가
    @GetMapping("/quote-td")
    public ResponseEntity<Map<String, Object>> quote(@RequestParam String pair) throws Exception {
        try {
            return ResponseEntity.ok(svc.fetchQuote(pair));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                // 현재가도 막히면 200 + 빈값
                return ResponseEntity.ok(Map.of("pair", pair, "last", Double.NaN, "source", "TwelveData quote (stale)"));
            }
            throw ex;
        }
    }
}
