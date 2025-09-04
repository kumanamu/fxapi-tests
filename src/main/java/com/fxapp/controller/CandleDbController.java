package com.fxapp.controller;

import com.fxapp.dto.CandlePoint;
import com.fxapp.service.CandleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CandleDbController {

    private final CandleService svc;

    /** DB에서 그대로 반환 */
    @GetMapping("/candles-db")
    public Map<String,Object> candlesDb(
            @RequestParam String pair,
            @RequestParam String tf,
            @RequestParam(required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue="400") int limit
    ){
        List<CandlePoint> points;
        if (from!=null && to!=null) points = svc.range(pair, tf, from, to);
        else points = svc.latest(pair, tf, limit);

        Map<String,Object> resp = new HashMap<>();
        resp.put("pair", pair);
        resp.put("tf", tf);
        resp.put("source", "DB");
        resp.put("points", points);
        return resp;
    }

    /** 다른 주기만 있어도 리샘플해서 제공 (예: 1m가 저장되어 있고 5m/30m/1d/1w/1mo/1y 요청 가능) */
    @GetMapping("/candles-db-resampled")
    public Map<String,Object> candlesDbResampled(
            @RequestParam String pair,
            @RequestParam String fromTf,   // 저장되어 있는 주기(예: 1m or 5m)
            @RequestParam String toTf,     // 프론트 요청 주기
            @RequestParam(defaultValue="2000") int take // base 취득량
    ){
        List<CandlePoint> points = svc.resampleFrom(fromTf, toTf, pair, take);
        Map<String,Object> resp = new HashMap<>();
        resp.put("pair", pair);
        resp.put("tf", toTf);
        resp.put("source", "DB-resampled("+fromTf+"→"+toTf+")");
        resp.put("points", points);
        return resp;
    }
}
