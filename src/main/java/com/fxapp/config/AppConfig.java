// src/main/java/com/fxapp/config/AppConfig.java
package com.fxapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    // ✅ 반드시 이름을 cacheManager가 아닌 'exhClient' 등으로
    @Bean("twelveClient")
    public WebClient twelveClient(
            @Value("${twelve.base-url:https://api.twelvedata.com}") String baseUrl
    ) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    // ✅ EXH를 실제로 쓰지 않더라도, 이름만 맞는 빈을 제공해서 주입 오류를 막습니다.
    @Bean("exhClient")
    public WebClient exhClient(
            @Value("${exh.base-url:https://api.exchangerate.host}") String baseUrl
    ) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}