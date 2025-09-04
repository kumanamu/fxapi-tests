package com.fxapp.controller;

import com.fxapp.dto.Candle;
import com.fxapp.service.AlphaVantageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AlphaVantageController {

    private final AlphaVantageService svc;

    // ex) /api/candles-av?pair=USD-KRW&tf=5m
    // tf: 5m,15m,30m,60m,1d,1mo,1y
    @GetMapping("/candles-av")
    public Map<String, Object> candles(@RequestParam String pair, @RequestParam String tf) {
        List<Candle> pts = svc.candles(pair, tf);
        return Map.of("pair", pair, "tf", tf, "points", pts, "source", "AlphaVantage " + tf);
    }
}
