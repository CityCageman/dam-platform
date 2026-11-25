package com.dam.governance.standard.client;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 调用运维工单 API 创建整改工单，不直接写平台运维库。 */
@Component
public class TicketCreateClient {

    private final RestClient client;

    public TicketCreateClient(@Value("${dam.operations-url:http://127.0.0.1:8096}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void create(Map<String, Object> ticket) {
        client.post().uri("/api/operations/tickets")
                .header("token", token())
                .body(ticket)
                .retrieve().body(Map.class);
    }

    private String token() {
        try { return StpUtil.getTokenValue(); } catch (Exception e) { return ""; }
    }
}
