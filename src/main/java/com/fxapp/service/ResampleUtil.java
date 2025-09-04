package com.fxapp.service;

import com.fxapp.dto.CandlePoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public class ResampleUtil {

    public static Duration tfToDuration(String tf) {
        return switch (tf) {
            case "1m" -> Duration.ofMinutes(1);
            case "5m" -> Duration.ofMinutes(5);
            case "30m"-> Duration.ofMinutes(30);
            case "1d" -> Duration.ofDays(1);
            case "1w" -> Duration.ofDays(7);
            default -> Duration.ofDays(1); // fallback
        };
    }

    public static Instant bucketStart(Instant t, String tf, ZoneId zone) {
        ZonedDateTime z = t.atZone(zone);
        return switch (tf) {
            case "1m" -> z.withSecond(0).withNano(0).toInstant();
            case "5m" -> z.withMinute((z.getMinute()/5)*5).withSecond(0).withNano(0).toInstant();
            case "30m"-> z.withMinute((z.getMinute()/30)*30).withSecond(0).withNano(0).toInstant();
            case "1d" -> z.toLocalDate().atStartOfDay(zone).toInstant();
            case "1w" -> z.with(java.time.DayOfWeek.MONDAY).toLocalDate().atStartOfDay(zone).toInstant();
            case "1mo"-> z.withDayOfMonth(1).toLocalDate().atStartOfDay(zone).toInstant();
            case "1y" -> z.withDayOfYear(1).toLocalDate().atStartOfDay(zone).toInstant();
            default   -> z.toLocalDate().atStartOfDay(zone).toInstant();
        };
    }

    /** 다운샘플: 작은 캔들을 큰 캔들로 */
    public static List<CandlePoint> downsample(List<CandlePoint> src, String tgtTf, ZoneId zone) {
        if (src.isEmpty()) return List.of();
        Map<Instant, List<CandlePoint>> grouped = new TreeMap<>();
        for (var c : src) {
            Instant b = bucketStart(c.t, tgtTf, zone);
            grouped.computeIfAbsent(b, k->new ArrayList<>()).add(c);
        }
        List<CandlePoint> out = new ArrayList<>();
        for (var e : grouped.entrySet()) {
            List<CandlePoint> list = e.getValue().stream()
                    .sorted(Comparator.comparing(cp -> cp.t))
                    .collect(Collectors.toList());
            var o = list.get(0).o;
            var h = list.stream().map(cp->cp.h).max(Comparator.naturalOrder()).orElse(o);
            var l = list.stream().map(cp->cp.l).min(Comparator.naturalOrder()).orElse(o);
            var c = list.get(list.size()-1).c;
            out.add(new CandlePoint(e.getKey(), o,h,l,c));
        }
        return out;
    }

    /** 업샘플(합성): 큰 캔들을 작은 타임프레임으로 */
    public static List<CandlePoint> upsample(List<CandlePoint> src, String tgtTf, ZoneId zone) {
        if (src.isEmpty()) return List.of();
        Duration step = tfToDuration(tgtTf);
        List<CandlePoint> out = new ArrayList<>();
        for (int i=0;i<src.size();i++){
            CandlePoint cur = src.get(i);
            Instant start = cur.t;
            Instant end = (i+1 < src.size()) ? src.get(i+1).t : start.plus(step);
            Instant t = start;
            BigDecimal prev = cur.o;
            while (!t.isAfter(end)) {
                // 선형 보간 + 소폭 랜덤(원 범위 내 클램프)
                double ratio = (double) Duration.between(start,t).toMillis() / Math.max(1, Duration.between(start,end).toMillis());
                BigDecimal c = cur.o.add(cur.c.subtract(cur.o).multiply(BigDecimal.valueOf(ratio)))
                        .add(BigDecimal.valueOf(Math.sin(ratio*6)*0.001).multiply(cur.h.subtract(cur.l)))
                        .setScale(6, RoundingMode.HALF_UP);
                BigDecimal o = prev;
                BigDecimal h = o.max(c);
                BigDecimal l = o.min(c);
                out.add(new CandlePoint(t, o,h,l,c));
                prev = c;
                t = t.plus(step);
            }
        }
        // 동일 버킷 시작정렬
        out.sort(Comparator.comparing(cp -> cp.t));
        return out;
    }
}
