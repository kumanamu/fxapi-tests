//package com.fxapp.controller;
//
//import com.fxapp.dto.ChartPointDto;
//import com.fxapp.service.ChartService;
//import org.springframework.web.bind.annotation.*;
//import java.util.List;
//
//@RestController
//@RequestMapping("/api")
//public class RateController {
//
//    private final ChartService chartService;
//
//    public RateController(ChartService chartService) {
//        this.chartService = chartService;
//    }
//
//    // 예: /api/chart?base=USD&quote=KRW&days=30
//    @GetMapping("/chart")
//    public List<ChartPointDto> chart(
//            @RequestParam String base,
//            @RequestParam String quote,
//            @RequestParam(defaultValue = "30") int days
//    ) {
//        return chartService.getDailySeries(base, quote, days);
//    }
//}
//
