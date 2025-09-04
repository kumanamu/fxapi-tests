
package com.fxapp.service;
import com.fxapp.FrankfurterClient;
import com.fxapp.LiveFxClient;
import com.fxapp.RateDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.time.Instant;
@Service
public class RateService {
    private final LiveFxClient live;
    private final FrankfurterClient ecb;
    public RateService(LiveFxClient live, FrankfurterClient ecb){
        this.live = live; this.ecb = ecb;
    }
    @Cacheable(value="liveRates", key="#base + '/' + #quote", unless="#result == null || #result.price() <= 0")
    public RateDto getQuote(String base, String quote, boolean member){
        double px = (member ? live.getRate(base, quote) : ecb.getRate(base, quote)).block();
        return new RateDto(base, quote, px, Instant.now().toString(), !member);
    }
}
