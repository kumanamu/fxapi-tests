
package com.fxapp;
import com.fxapp.service.RateService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.web.reactive.function.client.WebClient;
public class RateServiceTest {
    static MockWebServer liveServer, ecbServer;
    @BeforeAll static void setup() throws Exception { liveServer = new MockWebServer(); ecbServer = new MockWebServer(); liveServer.start(); ecbServer.start(); }
    @AfterAll static void tear() throws Exception { liveServer.shutdown(); ecbServer.shutdown(); }
    @Test
    void memberGetsLiveGuestGetsEcb() {
        liveServer.enqueue(new MockResponse().setBody("{\"rates\":{\"KRW\":1333.00}}").addHeader("Content-Type","application/json"));
        ecbServer.enqueue(new MockResponse().setBody("{\"rates\":{\"KRW\":1328.00}}").addHeader("Content-Type","application/json"));
        LiveFxClient live = new LiveFxClient(WebClient.create(liveServer.url("/").toString()));
        FrankfurterClient ecb = new FrankfurterClient(WebClient.create(ecbServer.url("/").toString()));
        RateService svc = new RateService(live, ecb);
        RateDto m = svc.getQuote("USD","KRW", true);
        RateDto g = svc.getQuote("USD","KRW", false);
        assertThat(m.delayed()).isFalse();
        assertThat(g.delayed()).isTrue();
        assertThat(m.price()).isEqualTo(1333.00);
        assertThat(g.price()).isEqualTo(1328.00);
    }
}
