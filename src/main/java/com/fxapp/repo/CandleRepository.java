package com.fxapp.repo;

import com.fxapp.domain.Candle;
import com.fxapp.domain.CandleId;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CandleRepository extends JpaRepository<Candle, CandleId> {

    // 구간 조회 (프론트 제공용)
    @Query("""
      select c from Candle c
      where c.id.pair = :pair and c.id.timeframe = :tf
        and c.id.bucketStart between :from and :to
      order by c.id.bucketStart asc
    """)
    List<Candle> findRange(
            @Param("pair") String pair,
            @Param("tf") String timeframe,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // 최신 N개
    @Query("""
      select c from Candle c
      where c.id.pair = :pair and c.id.timeframe = :tf
      order by c.id.bucketStart desc
    """)
    List<Candle> findLastN(@Param("pair") String pair,
                           @Param("tf") String timeframe,
                           org.springframework.data.domain.Pageable pageable);

    // MySQL 업서트 (OHLC 병합 정책: high=max, low=min, close=새값, open=최초값 유지)
    @Modifying
    @Query(value = """
      INSERT INTO candle (pair, timeframe, bucket_start, open, high, low, close, source, created_at, updated_at)
      VALUES (:pair, :tf, :bucketStart, :open, :high, :low, :close, :source, NOW(6), NOW(6))
      ON DUPLICATE KEY UPDATE
        high = GREATEST(high, VALUES(high)),
        low  = LEAST(low,  VALUES(low)),
        close= VALUES(close),
        source=VALUES(source),
        updated_at = NOW(6)
    """, nativeQuery = true)
    int upsert(
            @Param("pair") String pair,
            @Param("tf") String timeframe,
            @Param("bucketStart") Instant bucketStart,
            @Param("open") java.math.BigDecimal open,
            @Param("high") java.math.BigDecimal high,
            @Param("low")  java.math.BigDecimal low,
            @Param("close")java.math.BigDecimal close,
            @Param("source") String source
    );
}
