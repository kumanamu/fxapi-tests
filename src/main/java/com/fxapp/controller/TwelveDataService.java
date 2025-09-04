

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxapp.config.TwelveDataProps;
import lombok.RequiredArgsConstructor;
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

    private final WebClient webClient;         // ✅ 단일 WebClient (Twelve Data)
    private final ObjectMapper mapper;
    private final TwelveDataProps props;
    private final CacheManager cacheManager;

    private static final String CACHE = "td:fx";

    public Map<String, Object> fetchCandles(String pair, String tf, int limit) throws Exception {
        String key = cacheKey(pair, tf, limit);
        try {
            Map<String, Object> live = timeSeries(pair, tf, limit);
            putCache(key, live);
            return live;
        } catch (Exception ex) {
            Map<String, Object> stale = staleOrNull(pair, tf, limit);
            if (stale != null) return stale;
            if (ex instanceof ResponseStatusException rse) throw rse;
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream error", ex);
        }
    }

    public Map<String, Object> fetchQuote(String pair) throws Exception {
        String symbol = pairToSymbol(pair);
        String url = "/quote?symbol=" + symbol + "&apikey=" + props.getApiKey();
        String body = webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
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

    private Map<String, Object> timeSeries(String pair, String tf, int limit) throws Exception {
        String symbol = pairToSymbol(pair);
        String interval = switch (tf) {
            case "1m"  -> "1min";
            case "5m"  -> "5min";
            case "15m" -> "15min";
            case "30m" -> "30min";
            case "60m" -> "1h";
            case "1d"  -> "1day";
            case "1mo" -> "1month";
            case "1y"  -> "1day"; // 1년: 일봉으로 받아 슬라이스
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported tf: " + tf);
        };
        int outSize = ("1y".equals(tf)) ? Math.max(365, limit) : Math.max(120, limit);

        String url = "/time_series?symbol=" + symbol +
                "&interval=" + interval +
                "&outputsize=" + outSize +
                "&order=ASC&timezone=Asia/Seoul" +
                "&apikey=" + props.getApiKey();

        String body = webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
        JsonNode root = mapper.readTree(body);

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
        pts.sort(Comparator.comparing(m -> (String)m.get("t")));

        if ("1y".equals(tf) && pts.size() > 365) pts = pts.subList(pts.size() - 365, pts.size());
        if (limit > 0 && pts.size() > limit) pts = pts.subList(pts.size() - limit, pts.size());

        Map<String,Object> result = new HashMap<>();
        result.put("pair", pair);
        result.put("tf", tf);
        result.put("source", "TwelveData " + tf);
        result.put("points", pts);
        result.put("stale", false);
        return result;
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

    public Map<String,Object> fallbackDaily(String pair, int limit) {
        try { return timeSeries(pair, "1d", Math.max(limit, 200)); }
        catch (Exception e) { return null; }
    }
}
