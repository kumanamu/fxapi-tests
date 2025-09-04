
package com.fxapp;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import static org.assertj.core.api.Assertions.assertThat;
public class LiveFxClientTest {
    static MockWebServer server;
    @BeforeAll static void setup() throws Exception { server = new MockWebServer(); server.start(); }
    @AfterAll static void tear() throws Exception { server.shutdown(); }
    @Test
    void parsesLiveRate() {
        server.enqueue(new MockResponse().setBody("{\"rates\":{\"KRW\":1333.55}}").addHeader("Content-Type","application/json"));
        LiveFxClient client = new LiveFxClient(WebClient.create(server.url("/").toString()));
        double rate = client.getRate("USD","KRW").block();
        assertThat(rate).isEqualTo(1333.55);
    }
}
