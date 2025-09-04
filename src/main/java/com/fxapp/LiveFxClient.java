
package com.fxapp;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
@Component
public class LiveFxClient {
    private final WebClient wc;
    public LiveFxClient(WebClient liveWebClient){ this.wc = liveWebClient; }
    public Mono<Double> getRate(String base, String quote){
        return wc.get().uri(uri -> uri.path("/latest").queryParam("base", base).queryParam("symbols", quote).build())
            .retrieve().bodyToMono(JsonNode.class)
            .map(j -> j.path("rates").path(quote).asDouble());
    }
}
