package com.fxapp.controller;

import com.fxapp.service.TwelveDataService;
import jdk.internal.access.JavaIOFileDescriptorAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class TwelveDataController {

    private final TwelveDataService td; // 주입

    @GetMapping("/api/td-candles")
    public ResponseEntity<?> candles(
            @RequestParam String pair,
            @RequestParam String tf,
            @RequestParam(defaultValue = "240") int limit
    ) {
        String interval = mapInterval(tf);  // ★ 여기 중요

        // 예: WebClient 사용 시
        var uri = UriComponentsBuilder.fromHttpUrl("https://api.twelvedata.com/time_series")
                .queryParam("symbol", pair)       // 예: USD/KRW
                .queryParam("interval", interval) // 예: 1h / 1day / 1week / 1month / 30min ...
                .queryParam("outputsize", limit)
                .queryParam("order", "ASC")
                .queryParam("timezone", "UTC")
                .queryParam("apikey", apiKey)
                .build(true) // 인코딩 유지
                .toUri();

        var body = webClient.get().uri(uri)
                .retrieve()
                .bodyToMono(Map.class) // 또는 JsonNode
                .block();

        return ResponseEntity.ok(body);
    }
    private static String mapInterval(String tfRaw) {
        if (tfRaw == null) throw new IllegalArgumentException("tf is null");
        String tf = tfRaw.trim();
        switch (tf) {
            case "1m": return "1min";
            case "5m": return "5min";
            case "15m": return "15min";
            case "30m": return "30min";
            case "45m": return "45min";
            case "60m":
            case "1hour":
            case "1h": return "1h";
            case "2h": return "2h";
            case "4h": return "4h";
            case "8h": return "8h";
            case "1d":
            case "1day": return "1day";
            case "1w":
            case "1week": return "1week";
            case "1M":
            case "1mo":
            case "1month": return "1month";
        }
        throw new IllegalArgumentException("Unsupported tf: " + tfRaw);
    }
}
}