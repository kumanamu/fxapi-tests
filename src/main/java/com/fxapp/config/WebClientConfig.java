// com/fxapp/config/WebClientConfig.java
package com.fxapp.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(TwelveProps.class)
public class WebClientConfig {

    @Bean
    @Qualifier("twelveClient")
    public WebClient twelveClient(TwelveProps props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl()) // https://api.twelvedata.com
                .build();
    }
}
