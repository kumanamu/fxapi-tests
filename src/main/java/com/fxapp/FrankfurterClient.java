
package com.fxapp;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
@Component
public class FrankfurterClient {
    private final WebClient wc;
    public FrankfurterClient(WebClient ecbWebClient){ this.wc = ecbWebClient; }
    public Mono<Double> getRate(String base, String quote){
        return wc.get().uri(uri -> uri.path("/latest").queryParam("from", base).queryParam("to", quote).build())
            .retrieve().bodyToMono(JsonNode.class)
            .map(j -> j.path("rates").path(quote).asDouble());
    }
}
