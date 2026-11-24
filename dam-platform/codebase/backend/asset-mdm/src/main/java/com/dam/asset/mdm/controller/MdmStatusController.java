package com.dam.asset.mdm.controller;

import com.dam.platform.common.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/mdm")
public class MdmStatusController {
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(Map.of("service", "asset-mdm", "status", "UP", "role", "master data"));
    }
}
