// src/main/java/com/fxapp/service/ChartService.java
package com.fxapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fxapp.dto.ChartPointDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChartService {
    private final WebClient frankfurter; // baseUrl: https://api.frankfurter.dev

    public ChartService(WebClient ecbWebClient) {
        this.frankfurter = ecbWebClient;
    }

    /** 최근 n일 USD→KRW 같은 일봉 시계열 (실패 시 안전 폴백) */
    public List<ChartPointDto> getDailySeries(String base, String quote, int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);

        try {
            JsonNode json = frankfurter.get()
                    .uri(uri -> uri.path("/" + start + ".." + end)
                            .queryParam("from", base)
                            .queryParam("to", quote)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode rates = json.path("rates");
            List<ChartPointDto> list = new ArrayList<>();
            rates.fieldNames().forEachRemaining(date -> {
                double px = rates.path(date).path(quote).asDouble();
                list.add(new ChartPointDto(date, px));
            });
            return list.stream()
                    .sorted(Comparator.comparing(ChartPointDto::t))
                    .collect(Collectors.toList());
        } catch (WebClientResponseException e) {
            // HTTP 에러일 때: 서버 메시지 로그 남기고 폴백
            System.err.println("Frankfurter HTTP error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
            return fallbackSeries(base, quote, days);
        } catch (Exception e) {
            // 네트워크/파싱 등 모든 예외 폴백
            System.err.println("Frankfurter call failed: " + e.getMessage());
            return fallbackSeries(base, quote, days);
        }
    }

    /** 네트워크가 막혀도 데모 가능한 폴백(단순 계단형 더미 데이터) */
    private List<ChartPointDto> fallbackSeries(String base, String quote, int days) {
        List<ChartPointDto> list = new ArrayList<>();
        LocalDate end = LocalDate.now();
        double p = 1300.0; // 대략 USD/KRW 기준치
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = end.minusDays(i);
            // 간단한 난수 변동 (±1.2)
            p += (Math.random() - 0.5) * 2.4;
            list.add(new ChartPointDto(d.toString(), Math.round(p * 100.0) / 100.0));
        }
        return list;
    }
}
