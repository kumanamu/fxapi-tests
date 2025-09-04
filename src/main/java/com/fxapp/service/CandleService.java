package com.fxapp.service;

import com.fxapp.domain.Candle;
import com.fxapp.domain.CandleId;
import com.fxapp.dto.CandlePoint;
import com.fxapp.repo.CandleRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CandleService {
    private final CandleRepository repo;
    private static final ZoneId ZONE = ZoneId.of("UTC"); // 저장은 UTC 기준 권장

    @Transactional
    public int upsertBatch(String pair, String timeframe, String source, List<CandlePoint> points) {
        int count=0;
        for (var p : points) {
            CandleId id = new CandleId(pair, timeframe, p.t);
            count += repo.upsert(pair, timeframe, p.t, nz(p.o), nz(p.h), nz(p.l), nz(p.c), source);
        }
        return count;
    }

    private BigDecimal nz(BigDecimal v) { return v!=null? v : BigDecimal.ZERO; }

    public List<CandlePoint> latest(String pair, String timeframe, int limit) {
        var rows = repo.findLastN(pair, timeframe, PageRequest.of(0, limit));
        List<CandlePoint> out = new ArrayList<>(rows.size());
        for (int i = rows.size()-1; i>=0; i--) { // asc 로 변환
            var c = rows.get(i);
            out.add(new CandlePoint(
                    c.getId().getBucketStart(),
                    c.getOpen(), c.getHigh(), c.getLow(), c.getClose()
            ));
        }
        return out;
    }

    public List<CandlePoint> range(String pair, String timeframe, Instant from, Instant to) {
        var rows = repo.findRange(pair, timeframe, from, to);
        List<CandlePoint> out = new ArrayList<>(rows.size());
        for (var c : rows) {
            out.add(new CandlePoint(
                    c.getId().getBucketStart(),
                    c.getOpen(), c.getHigh(), c.getLow(), c.getClose()
            ));
        }
        return out;
    }

    /** 저장된 다른 주기로부터 리샘플해 제공 */
    public List<CandlePoint> resampleFrom(String baseTf, String targetTf, String pair, int take) {
        // 1) 저장된 기본주기에서 최근 N개 취득 (예: 1m 또는 5m를 베이스로 추천)
        var base = latest(pair, baseTf, take);
        // 2) 업/다운샘플
        List<CandlePoint> result;
        if (isDownsample(baseTf, targetTf)) result = ResampleUtil.downsample(base, targetTf, ZONE);
        else result = ResampleUtil.upsample(base, targetTf, ZONE);
        return result;
    }

    private boolean isDownsample(String base, String target) {
        List<String> order = List.of("1m","5m","30m","1d","1w","1mo","1y");
        return order.indexOf(base) < order.indexOf(target);
    }
}
