package com.fxapp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class TwelveDataController {

    // ✅ 주입받아 쓰도록 수정
    private final WebClient webClient;

    // ✅ application.yml(/properties)에서 주입
    @Value("${twelvedata.apikey}")
    private String apiKey;

    @GetMapping("/api/td-candles")
    public ResponseEntity<?> candles(
            @RequestParam String pair,
            @RequestParam String tf,
            @RequestParam(defaultValue = "240") int limit
    ) {
        final String interval = mapInterval(tf); // ★ 여기 중요 (1hour → 1h 등)

        final URI uri = UriComponentsBuilder.fromHttpUrl("https://api.twelvedata.com/time_series")
                .queryParam("symbol", pair)        // 예: USD/KRW
                .queryParam("interval", interval)  // 예: 1h / 1day / 1week / 1month / 30min ...
                .queryParam("outputsize", limit)
                .queryParam("order", "ASC")
                .queryParam("timezone", "UTC")
                .queryParam("apikey", apiKey)
                .build(true) // 쿼리 값 인코딩 상태 유지(USD/KRW 슬래시 살림)
                .toUri();

        // TwelveData는 200 OK로도 {status:error}를 줄 수 있으니 내용 점검
        Map<?, ?> body = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (body != null) {
            Object status = body.get("status");
            if (status != null && "error".equalsIgnoreCase(String.valueOf(status))) {
                // 예: interval 잘못(1hour) → 여기로 들어옴. 프런트/매핑 수정 후에는 안 와야 정상.
                return ResponseEntity.badRequest().body(body);
            }
        }

        return ResponseEntity.ok(body);
    }

    // ✅ interval 매핑 유틸: 여기(컨트롤러 안)에 둬도 됩니다.
    private static String mapInterval(String tfRaw) {
        if (tfRaw == null) throw new IllegalArgumentException("tf is null");
        String tf = tfRaw.trim();
        switch (tf) {
            case "1m":   return "1min";
            case "5m":   return "5min";
            case "15m":  return "15min";
            case "30m":  return "30min";
            case "45m":  return "45min";
            case "60m":
            case "1hour":
            case "1h":   return "1h";
            case "2h":   return "2h";
            case "4h":   return "4h";
            case "8h":   return "8h";
            case "1d":
            case "1day": return "1day";
            case "1w":
            case "1week":return "1week";
            case "1M":
            case "1mo":
            case "1month": return "1month";
        }
        throw new IllegalArgumentException("Unsupported tf: " + tfRaw);
    }
}
