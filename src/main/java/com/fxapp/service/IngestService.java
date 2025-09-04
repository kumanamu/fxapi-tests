package com.fxapp.service;

import com.fxapp.dto.CandlePoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IngestService {
    private final CandleService candleService;
    private final WebClient webClient; // 이미 구성된 exh/alphavantage/twelvedata 중 하나

    // 예: TwelveData 타임시리즈(의사 코드)
    public int ingestTwelveData(String pair, String tf, String apiKey, int size) {
        String symbol = pair.replace("-", "/"); // "USD-KRW" → "USD/KRW" 등, 공급자 형식에 맞춤
        Map resp = webClient.get()
                .uri(uri -> uri.path("/time_series")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", tf)
                        .queryParam("outputsize", size)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve().bodyToMono(Map.class).block();

        // 공급자 응답 포맷에 맞게 파싱 (여기선 가상의 키 사용)
        List<Map<String,String>> values = (List<Map<String,String>>) resp.get("values");
        List<CandlePoint> points = new ArrayList<>();
        for (var v : values) {
            Instant t = Instant.parse(v.get("datetime")+":00Z"); // 공급자 형식 맞게 수정
            BigDecimal o = new BigDecimal(v.get("open"));
            BigDecimal h = new BigDecimal(v.get("high"));
            BigDecimal l = new BigDecimal(v.get("low"));
            BigDecimal c = new BigDecimal(v.get("close"));
            points.add(new CandlePoint(t,o,h,l,c));
        }
        // 시간 오름차순으로 역정렬 필요시 정렬
        points.sort((a,b)-> a.t.compareTo(b.t));

        return candleService.upsertBatch(pair, tf, "TwelveData", points);
    }
}
