package com.fxapp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class TwelveDataCacheConfig {

    private Caffeine<Object, Object> spec(long seconds) {
        return Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(Duration.ofSeconds(seconds));
    }

    @Bean
    public CacheResolver tdCacheResolver() {
        // tf별로 서로 다른 TTL의 CaffeineCache를 직접 보유
        Map<String, Cache> buckets = new HashMap<>();
        buckets.put("td-1m",  new CaffeineCache("td-1m",  spec(12).build()));
        buckets.put("td-5m",  new CaffeineCache("td-5m",  spec(20).build()));
        buckets.put("td-15m", new CaffeineCache("td-15m", spec(30).build()));
        buckets.put("td-30m", new CaffeineCache("td-30m", spec(45).build()));
        buckets.put("td-1h",  new CaffeineCache("td-1h",  spec(60).build()));
        buckets.put("td-1d",  new CaffeineCache("td-1d",  spec(300).build()));
        buckets.put("td-1w",  new CaffeineCache("td-1w",  spec(600).build()));
        buckets.put("td-1mo", new CaffeineCache("td-1mo", spec(900).build()));
        buckets.put("td-1y",  new CaffeineCache("td-1y",  spec(1200).build()));

        return new CacheResolver() {
            @Override
            public List<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context) {
                // 서비스 시그니처: getCandles(String pair, String tf, int limit)
                String tf = (context.getArgs().length >= 2 && context.getArgs()[1] != null)
                        ? String.valueOf(context.getArgs()[1]) : "1d";

                String name = switch (tf) {
                    case "1m"  -> "td-1m";
                    case "5m"  -> "td-5m";
                    case "15m" -> "td-15m";
                    case "30m" -> "td-30m";
                    case "1h"  -> "td-1h";
                    case "1w"  -> "td-1w";
                    case "1mo" -> "td-1mo";
                    case "1y"  -> "td-1y";
                    default     -> "td-1d";
                };

                Cache cache = buckets.getOrDefault(name, buckets.get("td-1d"));
                return List.of(cache);
            }
        };
    }
}
