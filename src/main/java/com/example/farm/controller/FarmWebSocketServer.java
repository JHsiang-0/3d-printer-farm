package com.example.farm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@ServerEndpoint("/ws/farm-status") // 前端大屏连接的 WebSocket 地址
public class FarmWebSocketServer {

    // 存放所有当前在线的前端大屏客户端
    private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        log.info("🖥️ 新的大屏接入！当前在线大屏数: {}", sessions.size());
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 发生错误", error);
    }

    /**
     * 向所有在线的大屏广播打印机最新状态 (JSON 格式)
     */
    public static void broadcastPrinterStatus(Object data) {
        if (sessions.isEmpty()) return;

        try {
            String jsonMessage = objectMapper.writeValueAsString(data);
            for (Session session : sessions) {
                if (session.isOpen()) {
                    session.getAsyncRemote().sendText(jsonMessage);
                }
            }
        } catch (Exception e) {
            log.error("广播消息失败", e);
        }
    }
}