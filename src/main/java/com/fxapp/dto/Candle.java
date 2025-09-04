package com.fxapp.dto;

import java.time.Instant;

public record Candle(Instant t, double o, double h, double l, double c) {}
