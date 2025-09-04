package com.fxapp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 프론트에서 호출: /api/series?pair=USD-KRW&tf=1d&member=true
 * - ExchangeRate.host /timeframe 엔드포인트 사용
 * - USD/XXX 는 직접 조회, USD가 아닌 쌍(예: JPY/KRW)은 USD 크로스레이트로 계산
 * - 외부 401/403/429 등 실패 시 예외를 던지지 않고 빈 리스트 반환 → 마지막에 합성(synthetic) 데이터로 보강
 */
@RestController
@RequestMapping("/api")
public class ApiSeriesController {

    private static final Logger log = LoggerFactory.getLogger(ApiSeriesController.class);

    private final WebClient exh;                 // WebClient 빈 (이름: exhClient)
    private final ObjectMapper om = new ObjectMapper();

    @Value("${app.exh.base-url:https://api.exchangerate.host}")
    String baseUrl;

    @Value("${app.exh.access-key:}")
    String accessKey;

    public ApiSeriesController(@Qualifier("exhClient") WebClient exh) {
        this.exh = exh;
    }

    @GetMapping("/series")
    public Map<String, Object> series(@RequestParam String pair,
                                      @RequestParam String tf,
                                      @RequestParam(defaultValue = "true") boolean member) {

        String base  = pair.substring(0, 3).toUpperCase();   // ex) USD
        String quote = pair.substring(4, 7).toUpperCase();   // ex) KRW

        // 조회 범위(넉넉히)
        int days = switch (tf) {
            case "1d" -> 30;   // 최근 30일
            case "4w" -> 120;  // 4개월 정도
            case "1mo" -> 180; // 6개월
            case "1y" -> 365;  // 1년
            default -> 30;
        };

        ZoneId KST = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(KST);
        // 무료 플랜/시간대 이슈 방지: 우선 '어제'까지로 고정 (원하면 member=true일 때 today로 바꿔도 됨)
        LocalDate end   = today.minusDays(1);
        LocalDate start = end.minusDays(days);

        List<Map<String, Object>> points;
        String source;

        if ("USD".equals(base)) {
            points = fetchUsdSeries(quote, start, end);
            source = "EXH /timeframe USD→" + quote;
        } else {
            // JPY/KRW = USDKRW / USDJPY (USD 크로스)
            points = fetchCrossSeries(base, quote, start, end);
            source = "EXH /timeframe cross via USD (" + base + "/" + quote + ")";
        }

        // 실패/차단 등으로 포인트가 비면 합성 데이터로라도 반환(프론트는 항상 차트를 그림)
        if (points.isEmpty()) {
            source += " → synthetic";
            points = syntheticSeries(quote, start, end);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pair", pair);
        body.put("tf", tf);
        body.put("delayed", !member); // member=false면 지연
        body.put("source", source);
        body.put("points", points);
        return body;
    }

    /** USD/XXX는 /timeframe + currencies=XXX로 직접 받기 (키 필수) */
    private List<Map<String,Object>> fetchUsdSeries(String quote, LocalDate start, LocalDate end) {
        try {
            String url = UriComponentsBuilder.fromUriString(baseUrl + "/timeframe")
                    .queryParam("start_date", start)
                    .queryParam("end_date",   end)
                    .queryParam("currencies", quote)           // ex) KRW
                    .queryParam("access_key", accessKey)       // 키 필수
                    .build().toUriString();

            String raw = exh.get().uri(url)
                    // 4xx/5xx여도 던지지 말고 바디 문자열로 받기 (로그 찍고 빈 리스트 반환)
                    .exchangeToMono(res -> res.bodyToMono(String.class).map(body -> {
                        log.info("EXH {} {}", res.statusCode().value(), mask(url));
                        log.info("EXH body: {}", body == null ? "null" : body.substring(0, Math.min(400, body.length())));
                        return body;
                    }))
                    .block();

            JsonNode json   = raw == null ? null : om.readTree(raw);
            JsonNode quotes = json == null ? null : json.path("quotes");

            List<Map<String,Object>> pts = new ArrayList<>();
            if (quotes != null && quotes.isObject() && quotes.size() > 0) {
                List<String> days = new ArrayList<>();
                quotes.fieldNames().forEachRemaining(days::add);
                Collections.sort(days);
                String key = "USD" + quote;                    // ex) USDKRW
                for (String d : days) {
                    JsonNode v = quotes.path(d).path(key);
                    if (v.isNumber()) pts.add(Map.of("t", d, "price", v.asDouble()));
                }
            }
            return pts;
        } catch (Exception e) {
            log.warn("fetchUsdSeries failed", e);
            return Collections.emptyList();
        }
    }

    /** XXX/YYY는 USD 크로스:  price = USDYYY / USDXXX  (예: JPY/KRW = USDKRW / USDJPY) */
    private List<Map<String,Object>> fetchCrossSeries(String baseCcy, String quoteCcy, LocalDate start, LocalDate end) {
        try {
            String url = UriComponentsBuilder.fromUriString(baseUrl + "/timeframe")
                    .queryParam("start_date", start)
                    .queryParam("end_date",   end)
                    .queryParam("currencies", quoteCcy + "," + baseCcy) // ex) KRW,JPY
                    .queryParam("access_key", accessKey)
                    .build().toUriString();

            String raw = exh.get().uri(url)
                    .exchangeToMono(res -> res.bodyToMono(String.class).map(body -> {
                        log.info("EXH {} {}", res.statusCode().value(), mask(url));
                        log.info("EXH body: {}", body == null ? "null" : body.substring(0, Math.min(400, body.length())));
                        return body;
                    }))
                    .block();

            JsonNode json   = raw == null ? null : om.readTree(raw);
            JsonNode quotes = json == null ? null : json.path("quotes");

            List<Map<String,Object>> pts = new ArrayList<>();
            if (quotes != null && quotes.isObject() && quotes.size() > 0) {
                List<String> days = new ArrayList<>();
                quotes.fieldNames().forEachRemaining(days::add);
                Collections.sort(days);
                for (String d : days) {
                    JsonNode day      = quotes.path(d);
                    JsonNode usdQuote = day.path("USD" + quoteCcy); // ex) USDKRW
                    JsonNode usdBase  = day.path("USD" + baseCcy);  // ex) USDJPY
                    if (usdQuote.isNumber() && usdBase.isNumber() && usdBase.asDouble() != 0.0) {
                        double cross = usdQuote.asDouble() / usdBase.asDouble(); // KRW/JPY
                        pts.add(Map.of("t", d, "price", cross));
                    }
                }
            }
            return pts;
        } catch (Exception e) {
            log.warn("fetchCrossSeries failed", e);
            return Collections.emptyList();
        }
    }

    private List<Map<String,Object>> syntheticSeries(String quote, LocalDate start, LocalDate end) {
        List<Map<String,Object>> pts = new ArrayList<>();
        int i = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1), i++) {
            double px = switch (quote) {
                case "KRW" -> 1300 + Math.sin(i/7.0)*5 + (Math.random()-0.5)*3;
                case "JPY" -> 10   + Math.sin(i/9.0)*0.2 + (Math.random()-0.5)*0.1;
                case "CNY" -> 7    + Math.sin(i/11.0)*0.05 + (Math.random()-0.5)*0.02;
                case "GBP" -> 0.78 + Math.sin(i/13.0)*0.01;
                case "EUR" -> 0.90 + Math.sin(i/15.0)*0.01;
                default -> 1.0;
            };
            pts.add(Map.of("t", d.toString(), "price", Math.round(px*100.0)/100.0));
        }
        return pts;
    }

    private String mask(String url) {
        return (accessKey == null || accessKey.isBlank()) ? url : url.replace(accessKey, "****");
    }
}
