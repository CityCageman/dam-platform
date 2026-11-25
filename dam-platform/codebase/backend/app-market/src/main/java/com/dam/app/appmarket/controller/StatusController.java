package com.dam.app.appmarket.controller;

import com.dam.platform.common.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/app-market")
public class StatusController {
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(Map.of("service", "app-market", "status", "UP"));
    }
}
