package com.fxapp.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class CandleId implements Serializable {
    private String pair;        // 예: "USD-KRW"
    private String timeframe;   // 예: "1m","5m","30m","1d","1w","1mo","1y"
    private Instant bucketStart; // 버킷 시작 UTC

    public CandleId() {}
    public CandleId(String pair, String timeframe, Instant bucketStart) {
        this.pair = pair; this.timeframe = timeframe; this.bucketStart = bucketStart;
    }

    public String getPair() { return pair; }
    public String getTimeframe() { return timeframe; }
    public Instant getBucketStart() { return bucketStart; }

    public void setPair(String pair) { this.pair = pair; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }
    public void setBucketStart(Instant bucketStart) { this.bucketStart = bucketStart; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CandleId)) return false;
        CandleId that = (CandleId) o;
        return Objects.equals(pair, that.pair)
                && Objects.equals(timeframe, that.timeframe)
                && Objects.equals(bucketStart, that.bucketStart);
    }
    @Override public int hashCode() { return Objects.hash(pair, timeframe, bucketStart); }
}
