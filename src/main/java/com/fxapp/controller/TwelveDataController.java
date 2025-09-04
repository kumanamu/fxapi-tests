// com/fxapp/controller/TwelveDataController.java
package com.fxapp.controller;

import com.fxapp.config.TwelveProps;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class TwelveDataController {

    private final WebClient twelveClient;
    private final TwelveProps props;

    public TwelveDataController(@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                                @org.springframework.beans.factory.annotation.Qualifier("twelveClient")
                                WebClient twelveClient,
                                TwelveProps props) {
        this.twelveClient = twelveClient;
        this.props = props;
    }

    /** 프론트: /api/td-candles?pair=USD-KRW&tf=5m&limit=240 */
    @GetMapping("/td-candles")
    public Map<String,Object> candles(@RequestParam String pair,
                                      @RequestParam String tf,
                                      @RequestParam(defaultValue = "240") int limit) {
        if (props.getApiKey()==null || props.getApiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Missing TWELVE_API_KEY");
        }

        final String symbol = pair.replace('-', '/');   // USD-KRW -> USD/KRW
        final String interval = mapInterval(tf);        // 5m -> 5min
        final int size = Math.max(1, Math.min(5000, limit));

        // 호출
        Map resp = twelveClient.get()
                .uri(uri -> uri.path("/time_series")
                        .queryParam("symbol", symbol)      // 슬래시는 WebClient가 자동 인코딩 (%2F)
                        .queryParam("interval", interval)  // 1min, 5min, 15min, 30min, 1h, 1day, 1week, 1month
                        .queryParam("outputsize", size)
                        .queryParam("order", "ASC")        // 시간 오름차순(차트에 적합)
                        .queryParam("timezone", "UTC")
                        .queryParam("apikey", props.getApiKey())
                        .build())
                .retrieve()
                .onStatus(s -> s.value() >= 400, r -> r.createException())
                .bodyToMono(Map.class)
                .block();

        if (resp == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Null response from TwelveData");
        }

        // TwelveData는 에러도 200으로 줄 수 있음
        String status = String.valueOf(resp.getOrDefault("status", "ok"));
        if (!"ok".equalsIgnoreCase(status)) {
            String msg = String.valueOf(resp.getOrDefault("message", "Error"));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TwelveData: " + msg);
        }

        // values 파싱
        List<Map<String,Object>> values = getArray(resp,"values");
        if (values == null) values = getArray(resp,"data");
        if (values == null) values = Collections.emptyList();

        List<Map<String,Object>> points = new ArrayList<>(values.size());
        for (Map<String,Object> v : values) {
            String dt = str(v.get("datetime"), v.get("time"));
            if (dt == null) continue;
            Instant t = toInstant(dt);
            BigDecimal o = toBD(v.get("open"));
            BigDecimal h = toBD(v.get("high"));
            BigDecimal l = toBD(v.get("low"));
            BigDecimal c = toBD(v.get("close"));
            if (o==null||h==null||l==null||c==null) continue;
            points.add(Map.of("t", t.toString(), "o", o, "h", h, "l", l, "c", c));
        }

        // 연봉(1y)은 월봉으로 내려 받아 프론트가 그대로 그리게 둡니다.
        // 만약 points가 비었으면(간헐적) 일봉으로 다운시프트
        if (points.isEmpty() && "1y".equals(tf)) {
            return candles(pair, "1mo", size);
        }
        if (points.isEmpty()) {
            // 마지막 안전망: 일봉 폴백
            Map<String,Object> fb = fallbackDaily(pair);
            if (fb != null) return fb;
        }

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("pair",   pair);
        out.put("tf",     tf);
        out.put("source", "TwelveData " + interval);
        out.put("points", points);
        return out;
    }

    // ---- helpers ----
    private static String mapInterval(String tf){
        switch (tf) {
            case "1m":  return "1min";
            case "5m":  return "5min";
            case "15m": return "15min";
            case "30m": return "30min";
            case "60m":
            case "1h":  return "1h";
            case "1d":  return "1day";
            case "1w":  return "1week";
            case "1mo": return "1month";
            case "1y":  return "1month"; // 월봉으로 대체
            default:    return "1day";
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> getArray(Map map, String key){
        Object v = map.get(key);
        return (v instanceof List) ? (List<Map<String,Object>>) v : null;
    }

    private static String str(Object... cands){
        for (Object o : cands) if (o!=null) return String.valueOf(o);
        return null;
    }
    private static Instant toInstant(String dt){
        String iso = dt.contains("T") ? dt : dt.replace(' ', 'T');
        if (!iso.endsWith("Z")) iso = iso + "Z";
        return Instant.parse(iso);
    }
    private static BigDecimal toBD(Object o){
        if (o==null) return null;
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        return new BigDecimal(s);
    }

    /** 일봉 폴백: exchangerate.host */
    private Map<String,Object> fallbackDaily(String pair){
        try{
            String[] sp = pair.split("-");
            String base = sp[0], quote = sp[1];
            Instant end = Instant.now();
            Instant start = end.minusSeconds(365L*24*3600);
            Map fb = twelveClient.get() // 절대 URL 사용 (baseUrl 무시)
                    .uri("https://api.exchangerate.host/timeframe?start_date={s}&end_date={e}&base={b}&symbols={q}",
                            start.toString().substring(0,10),
                            end.toString().substring(0,10),
                            base, quote)
                    .retrieve().bodyToMono(Map.class).block();
            if (fb == null || !Boolean.TRUE.equals(fb.get("success"))) return null;

            Map<String, Map<String, Map<String,Number>>> rates =
                    (Map<String, Map<String, Map<String,Number>>>) fb.get("rates");
            List<Map<String,Object>> points = new ArrayList<>();
            rates.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                String date = e.getKey();
                Map<String, Map<String, Number>> baseMap = e.getValue();
                Number close = baseMap.getOrDefault(quote, Collections.emptyMap()).get("rate");
                if (close != null) {
                    Instant t = Instant.parse(date + "T00:00:00Z");
                    BigDecimal c = BigDecimal.valueOf(close.doubleValue());
                    points.add(Map.of("t", t.toString(), "o", c, "h", c, "l", c, "c", c));
                }
            });
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("pair", pair); out.put("tf", "1d");
            out.put("source","EXH /timeframe " + pair.replace('-', '→'));
            out.put("points", points);
            return out;
        }catch(Exception ignore){ return null; }
    }
}
