package com.fxapp.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class CandlePoint {
    public Instant t;
    public BigDecimal o, h, l, c;

    public CandlePoint() {}
    public CandlePoint(Instant t, BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c) {
        this.t = t; this.o=o; this.h=h; this.l=l; this.c=c;
    }
}
