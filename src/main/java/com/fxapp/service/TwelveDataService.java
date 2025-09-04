package com.fxapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxapp.config.TwelveDataProps;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TwelveDataService {

    @Qualifier("twelveClient")
    private final WebClient tdClient;
    private final ObjectMapper mapper;
    private final TwelveDataProps props;
    private final CacheManager cacheManager;

    private static final String CACHE = "td:fx";

    /** /api/candles-td: 1m/5m/15m/30m/60m/1d/1mo/1y 지원 */
    public Map<String, Object> fetchCandles(String pair, String tf, int limit) throws Exception {
        String key = cacheKey(pair, tf, limit);
        try {
            Map<String, Object> live = timeSeries(pair, tf, limit);
            putCache(key, live);
            return live;
        } catch (Exception ex) {
            Map<String, Object> stale = staleOrNull(pair, tf, limit);
            if (stale != null) return stale; // ✅ 429/오류 시 캐시 반환(200)
            if (ex instanceof ResponseStatusException rse) throw rse;
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream error", ex);
        }
    }

    /** /api/quote-td: 현재가 */
    public Map<String, Object> fetchQuote(String pair) throws Exception {
        String symbol = pairToSymbol(pair); // "USD-KRW" -> "USD/KRW"
        String url = "/quote?symbol=" + symbol + "&apikey=" + props.getApiKey();
        String body = tdClient.get().uri(url).retrieve().bodyToMono(String.class).block();
        JsonNode root = mapper.readTree(body);

        if (root.has("status") && "error".equals(root.path("status").asText())) {
            String msg = root.path("message").asText("TwelveData error");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, msg);
        }
        Map<String,Object> result = new HashMap<>();
        result.put("pair", pair);
        result.put("last", root.path("price").asDouble(root.path("close").asDouble(Double.NaN)));
        result.put("bid", root.path("bid").asDouble(Double.NaN));
        result.put("ask", root.path("ask").asDouble(Double.NaN));
        result.put("source", "TwelveData quote");
        return result;
    }

    // ---------- 내부 구현 ----------

    private Map<String, Object> timeSeries(String pair, String tf, int limit) throws Exception {
        String symbol = pairToSymbol(pair); // "USD/KRW"
        String interval = mapTfToInterval(tf);
        int outSize = calcOutputSize(tf, limit);

        String url = "/time_series?symbol=" + symbol +
                "&interval=" + interval +
                "&outputsize=" + outSize +
                "&order=ASC&timezone=Asia/Seoul" +
                "&apikey=" + props.getApiKey();

        String body = tdClient.get().uri(url).retrieve().bodyToMono(String.class).block();
        JsonNode root = mapper.readTree(body);

        // 오류/한도 메시지 감지 → 429로 변환 (서비스 상위에서 stale로 치환)
        if (root.has("status") && "error".equals(root.path("status").asText())) {
            String msg = root.path("message").asText("TwelveData error");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, msg);
        }
        JsonNode values = root.path("values");
        if (values.isMissingNode() || !values.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "values not found");
        }

        List<Map<String,Object>> pts = new ArrayList<>();
        for (JsonNode v : values) {
            String t = v.path("datetime").asText();
            double o = v.path("open").asDouble();
            double h = v.path("high").asDouble();
            double l = v.path("low").asDouble();
            double c = v.path("close").asDouble();
            if (Double.isFinite(o) && Double.isFinite(h) && Double.isFinite(l) && Double.isFinite(c)) {
                pts.add(Map.of("t", t, "o", o, "h", h, "l", l, "c", c));
            }
        }
        // 이미 order=ASC로 요청했지만 혹시 몰라 한 번 더 정렬
        pts.sort(Comparator.comparing(m -> (String)m.get("t")));

        // tf=1y는 일봉 365개 정도만 슬라이스
        if ("1y".equals(tf) && pts.size() > 365) {
            pts = pts.subList(pts.size() - 365, pts.size());
        }
        if (limit > 0 && pts.size() > limit) {
            pts = pts.subList(pts.size() - limit, pts.size());
        }

        Map<String,Object> result = new HashMap<>();
        result.put("pair", pair);
        result.put("tf", tf);
        result.put("source", "TwelveData " + tf);
        result.put("points", pts);
        result.put("stale", false);
        return result;
    }

    private String mapTfToInterval(String tf) {
        return switch (tf) {
            case "1m"  -> "1min";
            case "5m"  -> "5min";
            case "15m" -> "15min";
            case "30m" -> "30min";
            case "60m" -> "1h";
            case "1d"  -> "1day";
            case "1mo" -> "1month";
            case "1y"  -> "1day";  // 1년치: 일봉으로 받아서 슬라이스
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported tf: " + tf);
        };
    }

    private int calcOutputSize(String tf, int limit) {
        if ("1y".equals(tf)) return Math.max(365, limit);
        return Math.max(120, limit);
    }

    private String pairToSymbol(String pair) {
        String[] p = pair.split("-");
        return p[0] + "/" + p[1];
    }

    private String cacheKey(String pair, String tf, int limit) { return pair + "|" + tf + "|" + limit; }

    private void putCache(String key, Map<String,Object> val){
        Cache c = cacheManager.getCache(CACHE);
        if (c != null) c.put(key, val);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> staleOrNull(String pair, String tf, int limit){
        Cache c = cacheManager.getCache(CACHE);
        if (c == null) return null;
        Map<String, Object> cached = c.get(cacheKey(pair, tf, limit), Map.class);
        if (cached == null) return null;
        Map<String,Object> copy = new HashMap<>(cached);
        copy.put("stale", true);
        copy.put("source", cached.getOrDefault("source","TwelveData") + " (stale)");
        return copy;
    }

    /** 일봉 대체(첫 호출부터 429일 때 최소 차트 유지) */
    public Map<String,Object> fallbackDaily(String pair, int limit) {
        try {
            return timeSeries(pair, "1d", Math.max(limit, 200));
        } catch (Exception e) {
            return null;
        }
    }
}
