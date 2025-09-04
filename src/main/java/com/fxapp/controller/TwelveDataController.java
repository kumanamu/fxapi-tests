// com/fxapp/controller/TwelveDataController.java
package com.fxapp.controller;

import com.fxapp.config.TwelveProps;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
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

    public TwelveDataController(@Qualifier("twelveClient") WebClient twelveClient, TwelveProps props) {
        this.twelveClient = twelveClient;
        this.props = props;
    }

    /** 프론트: /api/td-candles?pair=USD-KRW&tf=5m&limit=240 */
    @GetMapping("/td-candles")
    public Map<String,Object> candles(@RequestParam String pair,
                                      @RequestParam String tf,
                                      @RequestParam(defaultValue = "240") int limit) {
        if (props.getApiKey()==null || props.getApiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Missing twelve.api-key");
        }

        final String symbol   = pair.replace('-', '/');   // USD-KRW -> USD/KRW
        final String interval = mapInterval(tf);          // 5m -> 5min
        final int size        = Math.max(1, Math.min(5000, limit));

        Map resp = twelveClient.get()
                .uri(uri -> uri.path("/time_series")
                        .queryParam("symbol", symbol)      // WebClient가 / 를 %2F로 인코딩
                        .queryParam("interval", interval)  // 1min,5min,15min,30min,1h,1day,1week,1month
                        .queryParam("outputsize", size)
                        .queryParam("order", "ASC")
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

        // TwelveData는 에러도 200으로 내려줄 수 있음 → status/message 확인
        String status = String.valueOf(resp.getOrDefault("status", "ok"));
        if (!"ok".equalsIgnoreCase(status)) {
            String msg = String.valueOf(resp.getOrDefault("message", "Error"));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TwelveData: " + msg);
        }

        // values 파싱(혹시 data 키로 올 수도 있어서 보조 확인)
        List<Map<String,Object>> values = getArray(resp,"values");
        if (values == null) values = getArray(resp,"data");
        if (values == null) values = Collections.emptyList();

        List<Map<String,Object>> points = new ArrayList<>(values.size());
        for (Map<String,Object> v : values) {
            String dt = pickStr(v.get("datetime"), v.get("time"));
            if (dt == null) continue;
            Instant t = toInstant(dt);
            BigDecimal o = toBD(v.get("open"));
            BigDecimal h = toBD(v.get("high"));
            BigDecimal l = toBD(v.get("low"));
            BigDecimal c = toBD(v.get("close"));
            if (o==null||h==null||l==null||c==null) continue;
            points.add(Map.of("t", t.toString(), "o", o, "h", h, "l", l, "c", c));
        }

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("pair",   pair);
        out.put("tf",     tf);
        out.put("source", "TwelveData " + interval);
        out.put("points", points); // 비어 있으면 프론트에서 "데이터 0건" 표시
        return out;
    }

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
            case "1y":  return "1month"; // 연봉은 월봉으로 대체(프론트에서 그대로 사용)
            default:    return "1day";
        }
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> getArray(Map m, String key){
        Object v = m.get(key);
        return (v instanceof List) ? (List<Map<String,Object>>) v : null;
    }
    private static String pickStr(Object... cands){
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
}
