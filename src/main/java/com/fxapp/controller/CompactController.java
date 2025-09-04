// src/main/java/com/fxapp/controller/CompatController.java
package com.fxapp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fxapp.dto.ChartPointDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "legacy.exh.enabled", havingValue = "true", matchIfMissing = false
)
@RestController
public class CompactController {

    private final WebClient exh; // ExchangeRate.host WebClient

    // WebClientConfig/AppConfig에 정의한 이름과 맞추세요: @Bean WebClient exhClient()
    public CompactController(@Qualifier("exhClient") WebClient exh) {
        this.exh = exh;
    }

    /**
     * 호환 엔드포인트:
     *   /api/chart?base=USD&quote=KRW&days=30&member=true
     * 응답: [{ "t":"2025-08-20", "price":1234.56 }, ...]
     */
    @GetMapping("/rates/chart")
    public List<ChartPointDto> chart(@RequestParam String base,
                                     @RequestParam String quote,
                                     @RequestParam(defaultValue = "30") int days,
                                     @RequestParam(defaultValue = "true") boolean member) {

        ZoneId SEOUL = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(SEOUL);
        // 비회원은 어제까지만(지연) — 일봉 기준
        LocalDate end = member ? today : today.minusDays(1);
        LocalDate start = end.minusDays(Math.max(days, 1));

        // ExchangeRate.host /timeseries 호출
        JsonNode json = exh.get()
                .uri(uri -> uri.path("/timeseries")
                        .queryParam("start_date", start.toString())
                        .queryParam("end_date", end.toString())
                        .queryParam("base", base.toUpperCase())
                        .queryParam("symbols", quote.toUpperCase())
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        JsonNode rates = json.path("rates");
        List<ChartPointDto> list = new ArrayList<>();
        Iterator<String> it = rates.fieldNames();
        String q = quote.toUpperCase();
        while (it.hasNext()) {
            String d = it.next(); // yyyy-MM-dd
            double px = rates.path(d).path(q).asDouble();
            list.add(new ChartPointDto(d, px));
        }
        // 날짜 오름차순 정렬
        list.sort(Comparator.comparing(ChartPointDto::t));
        return list;
    }


}
