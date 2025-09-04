package com.fxapp.service;

import com.fxapp.dto.Candle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AlphaVantageService {

    @Value("${app.av.key:}")
    String apiKey;

    private final WebClient http = WebClient.builder()
            .baseUrl("https://www.alphavantage.co")
            .build();

    // 매우 간단한 메모리 캐시(충돌 줄이기 위해 TTL 운용)
    private static class CE { List<Candle> d; long exp; CE(List<Candle> d, long exp){this.d=d; this.exp=exp;} }
    private final Map<String, CE> cache = new ConcurrentHashMap<>();

    public List<Candle> candles(String pair, String tf) {
        String key = pair + "|" + tf;
        long now = System.currentTimeMillis();
        CE ce = cache.get(key);
        if (ce != null && ce.exp > now) return ce.d;

        List<Candle> out;
        switch (tf) {
            case "5m", "15m", "30m", "60m" -> {
                out = fetchIntraday(pair, tf);
                put(key, out, 60_000); // 60초
            }
            case "1d" -> {
                out = fetchDaily(pair);
                put(key, out, 5 * 60_000);
            }
            case "1mo" -> {
                out = fetchMonthly(pair);
                put(key, out, 60 * 60_000);
            }
            case "1y" -> {
                List<Candle> mon = fetchMonthly(pair);
                out = aggregateYearly(mon);
                put(key, out, 60 * 60_000);
            }
            default -> throw new IllegalArgumentException("unsupported tf: " + tf);
        }
        return out;
    }

    private void put(String k, List<Candle> d, long ttl) { cache.put(k, new CE(d, System.currentTimeMillis()+ttl)); }

    // ---------- Alpha Vantage 호출들 ----------

    // 분/시간봉: FX_INTRADAY (지원 간격: 5m,15m,30m,60m)
    private List<Candle> fetchIntraday(String pair, String tf) {
        String[] p = pair.split("-");
        String from = p[0], to = p[1];
        String interval = switch (tf) {
            case "5m", "15m", "30m", "60m" -> tf;
            default -> "60m";
        };
        var uri = UriComponentsBuilder.fromPath("/query")
                .queryParam("function", "FX_INTRADAY")
                .queryParam("from_symbol", from)
                .queryParam("to_symbol", to)
                .queryParam("interval", interval)
                .queryParam("outputsize", "compact") // 최근 데이터
                .queryParam("apikey", apiKey)
                .build(true).toString();

        Map<String, Object> json = http.get().uri(uri).retrieve().bodyToMono(Map.class).block();

        Map<String, Map<String, String>> series = findSeries(json, "Time Series FX");
        if (series == null) return List.of();

        // 키: "YYYY-MM-DD HH:mm:ss" (UTC 기준)
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return series.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // 시간 오름차순
                .map(en -> toCandle(en.getKey(), en.getValue(), f, true))
                .collect(Collectors.toList());
    }

    // 일봉: FX_DAILY
    private List<Candle> fetchDaily(String pair) {
        String[] p = pair.split("-");
        var uri = UriComponentsBuilder.fromPath("/query")
                .queryParam("function", "FX_DAILY")
                .queryParam("from_symbol", p[0])
                .queryParam("to_symbol", p[1])
                .queryParam("outputsize", "compact")
                .queryParam("apikey", apiKey)
                .build(true).toString();

        Map<String, Object> json = http.get().uri(uri).retrieve().bodyToMono(Map.class).block();
        Map<String, Map<String, String>> series = findSeries(json, "Time Series FX (Daily)");
        if (series == null) return List.of();

        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return series.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(en -> toCandle(en.getKey(), en.getValue(), f, false))
                .collect(Collectors.toList());
    }

    // 월봉: FX_MONTHLY
    private List<Candle> fetchMonthly(String pair) {
        String[] p = pair.split("-");
        var uri = UriComponentsBuilder.fromPath("/query")
                .queryParam("function", "FX_MONTHLY")
                .queryParam("from_symbol", p[0])
                .queryParam("to_symbol", p[1])
                .queryParam("apikey", apiKey)
                .build(true).toString();

        Map<String, Object> json = http.get().uri(uri).retrieve().bodyToMono(Map.class).block();
        Map<String, Map<String, String>> series = findSeries(json, "Time Series FX (Monthly)");
        if (series == null) return List.of();

        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return series.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(en -> toCandle(en.getKey(), en.getValue(), f, false))
                .collect(Collectors.toList());
    }

    // 월봉 → 연봉 집계
    private List<Candle> aggregateYearly(List<Candle> monthly) {
        if (monthly.isEmpty()) return monthly;
        Map<Integer, List<Candle>> byYear = monthly.stream()
                .collect(Collectors.groupingBy(c -> ZonedDateTime.ofInstant(c.t(), ZoneOffset.UTC).getYear(),
                        TreeMap::new, Collectors.toList()));

        List<Candle> out = new ArrayList<>();
        for (var e : byYear.entrySet()) {
            List<Candle> arr = e.getValue();
            arr.sort(Comparator.comparing(Candle::t));
            double o = arr.get(0).o();
            double c = arr.get(arr.size() - 1).c();
            double h = arr.stream().mapToDouble(Candle::h).max().orElse(o);
            double l = arr.stream().mapToDouble(Candle::l).min().orElse(o);
            Instant t = ZonedDateTime.of(LocalDate.of(e.getKey(), 1, 1).atStartOfDay(), ZoneOffset.UTC).toInstant();
            out.add(new Candle(t, o, h, l, c));
        }
        return out;
    }

    // ---------- 유틸 ----------
    private static Map<String, Map<String, String>> findSeries(Map<String, Object> json, String startsWith) {
        if (json == null) return null;
        for (var e : json.entrySet()) {
            String k = e.getKey();
            if (k != null && k.startsWith(startsWith)) {
                return (Map<String, Map<String, String>>) e.getValue();
            }
        }
        return null;
    }

    private static Candle toCandle(String key, Map<String, String> m, DateTimeFormatter f, boolean hasTime) {
        Instant t = hasTime
                ? LocalDateTime.parse(key, f).atOffset(ZoneOffset.UTC).toInstant()
                : LocalDate.parse(key, f).atStartOfDay(ZoneOffset.UTC).toInstant();
        double o = Double.parseDouble(m.get("1. open"));
        double h = Double.parseDouble(m.get("2. high"));
        double l = Double.parseDouble(m.get("3. low"));
        double c = Double.parseDouble(m.get("4. close"));
        return new Candle(t, o, h, l, c);
    }
}
