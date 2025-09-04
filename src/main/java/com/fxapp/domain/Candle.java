package com.fxapp.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "candle",
        uniqueConstraints = @UniqueConstraint(columnNames = {"pair","timeframe","bucket_start"}),
        indexes = {
                @Index(name="idx_candle_pair_tf_bucket", columnList = "pair,timeframe,bucket_start"),
                @Index(name="idx_candle_pair_tf", columnList = "pair,timeframe")
        })
public class Candle {

    @EmbeddedId
    private CandleId id;

    @Column(nullable=false, precision=20, scale=6)
    private BigDecimal open;

    @Column(nullable=false, precision=20, scale=6)
    private BigDecimal high;

    @Column(nullable=false, precision=20, scale=6)
    private BigDecimal low;

    @Column(nullable=false, precision=20, scale=6)
    private BigDecimal close;

    @Column(length=32)
    private String source;  // "TwelveData", "AlphaVantage", "EXH", etc.

    @Column(nullable=false, updatable=false)
    private Instant createdAt;

    @Column(nullable=false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now; updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Candle() {}
    public Candle(CandleId id, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, String source) {
        this.id = id; this.open=open; this.high=high; this.low=low; this.close=close; this.source = source;
    }

    public CandleId getId() { return id; }
    public BigDecimal getOpen() { return open; }
    public BigDecimal getHigh() { return high; }
    public BigDecimal getLow() { return low; }
    public BigDecimal getClose() { return close; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(CandleId id) { this.id = id; }
    public void setOpen(BigDecimal open) { this.open = open; }
    public void setHigh(BigDecimal high) { this.high = high; }
    public void setLow(BigDecimal low) { this.low = low; }
    public void setClose(BigDecimal close) { this.close = close; }
    public void setSource(String source) { this.source = source; }
}
