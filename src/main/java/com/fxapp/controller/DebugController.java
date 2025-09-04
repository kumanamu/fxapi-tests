package com.fxapp.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DebugController {
    @Value("${app.exh.access-key:}") String key;

    @GetMapping("/__props")
    public Map<String,Object> props(){
        return Map.of(
                "accessKeyPresent", key != null && !key.isBlank(),
                "accessKeyLen", key == null ? 0 : key.length()
        );
    }
}
