// src/main/java/com/fxapp/config/AppConfig.java
package com.fxapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    // ✅ 반드시 이름을 cacheManager가 아닌 'exhClient' 등으로
    @Bean
    public WebClient exhClient() {
        // 절대 URL을 넘겨서 호출하므로 baseUrl 없어도 동작하지만, 명시해둬도 무방
        return WebClient.create("https://api.exchangerate.host");
    }
}

