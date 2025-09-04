package com.fxapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TwelveDataService {

    private final WebClient webClient;

    @Value("${twelve.api-key}")
    private String apiKey;

    public record CandleResp(List<Map<String,Object>> points, String source) {}

    private String toInterval(String tf){
        return switch (tf){
            case "1m" -> "1min";
            case "5m" -> "5min";
            case "15m"-> "15min";
            case "30m"-> "30min";
            case "1h" -> "1hour";
            case "1w" -> "1week";
            case "1mo"-> "1month";
            case "1y" -> "1year";
            default   -> "1day";
        };
    }
    private static final DateTimeFormatter TD_LDT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private Instant parseTdInstant(String s){
        if (s == null) return null;
        if (s.length()==10) { // "yyyy-MM-dd"
            return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        if (s.indexOf('T')>0) { // ISO
            String iso = s.endsWith("Z")? s : s+"Z";
            return Instant.parse(iso);
        }
        // "yyyy-MM-dd HH:mm:ss"
        LocalDateTime ldt = LocalDateTime.parse(s, TD_LDT);
        return ldt.toInstant(ZoneOffset.UTC);
    }

    // 🔽 sync=true: 같은 키에 대한 동시요청을 1회 호출로 병합
    @Cacheable(cacheResolver = "tdCacheResolver",
            key = "#pair + '|' + #tf + '|' + #limit",
            sync = true)
    public CandleResp getCandles(String pair, String tf, int limit){

        String interval = toInterval(tf);
        String symbol = pair.replace('-', '/');

        URI uri = UriComponentsBuilder.fromHttpUrl("https://api.twelvedata.com/time_series")
                .queryParam("symbol", symbol)
                .queryParam("interval", interval)
                .queryParam("outputsize", limit)
                .queryParam("order", "ASC")
                .queryParam("timezone", "UTC")
                .queryParam("apikey", apiKey)
                .build(true).toUri();

        log.debug("TwelveData GET {}", uri);
        Map<String,Object> body = webClient.get().uri(uri).retrieve()
                .onStatus(HttpStatusCode::isError, resp->resp.bodyToMono(String.class)
                        .map(msg-> new ResponseStatusException(resp.statusCode(), "TwelveData: " + msg)))
                .bodyToMono(new ParameterizedTypeReference<Map<String,Object>>(){}).block();

        // values 파싱
        Object rawValues = body.get("values");
        if (!(rawValues instanceof List<?> list)) {
            // 에러 케이스(credit 초과 등)도 여기서 throw
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "TwelveData: " + String.valueOf(body));
        }

        List<Map<String,Object>> points = new ArrayList<>();
        for (Object o : list){
            Map<?,?> v = (Map<?,?>) o;
            String dt = String.valueOf(v.get("datetime"));
            Instant t = parseTdInstant(dt);

            BigDecimal o_ = new BigDecimal(String.valueOf(v.get("open")));
            BigDecimal h_ = new BigDecimal(String.valueOf(v.get("high")));
            BigDecimal l_ = new BigDecimal(String.valueOf(v.get("low")));
            BigDecimal c_ = new BigDecimal(String.valueOf(v.get("close")));

            Map<String,Object> one = new LinkedHashMap<>();
            one.put("t", t.toString());
            one.put("o", o_);
            one.put("h", h_);
            one.put("l", l_);
            one.put("c", c_);
            points.add(one);
        }
        // 오름차순 보장
        points.sort(Comparator.comparing(m -> Instant.parse((String)m.get("t"))));
        return new CandleResp(points, "twelvedata");
    }
}
