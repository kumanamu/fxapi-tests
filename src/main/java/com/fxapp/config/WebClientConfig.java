package com.fxapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        // baseUrl만 지정해두면 요청마다 path("/time_series")만 붙이면 됨
        return builder
                .baseUrl("https://api.twelvedata.com")
                .build();
    }
}
