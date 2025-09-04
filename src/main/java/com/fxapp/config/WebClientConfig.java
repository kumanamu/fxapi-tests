package com.fxapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // 프로젝트 전역에서 쓸 단 하나의 WebClient (Twelve Data 전용)
    @Bean
    public WebClient webClient(TwelveDataProps props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
    }
}
