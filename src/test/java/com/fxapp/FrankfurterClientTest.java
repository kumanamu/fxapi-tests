
package com.fxapp;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import static org.assertj.core.api.Assertions.assertThat;
public class FrankfurterClientTest {
    static MockWebServer server;
    @BeforeAll static void setup() throws Exception { server = new MockWebServer(); server.start(); }
    @AfterAll static void tear() throws Exception { server.shutdown(); }
    @Test
    void parsesEcbRate() {
        server.enqueue(new MockResponse().setBody("{\"rates\":{\"KRW\":1328.10}}").addHeader("Content-Type","application/json"));
        FrankfurterClient client = new FrankfurterClient(WebClient.create(server.url("/").toString()));
        double rate = client.getRate("USD","KRW").block();
        assertThat(rate).isEqualTo(1328.10);
    }
}
