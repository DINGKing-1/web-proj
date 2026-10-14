package com.secondhand.mall.controller;

import com.secondhand.mall.entity.ChatMessage;
import com.secondhand.mall.service.ChatService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 聊天实时推送（SSE）。不改动既有 REST 聊天 API。
 * 连接生命周期：断开 / 报错 / 被踢 / 心跳失败时立即移除订阅并关闭。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatSseController {

    private final ChatService chatService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4, r -> {
        Thread t = new Thread(r, "chat-sse");
        t.setDaemon(true);
        return t;
    });
    /** key = user:roomId → 连接句柄 */
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public ChatSseController(ChatService chatService) {
        this.chatService = chatService;
        // 心跳清扫：定期清理已失效/已关闭连接
        scheduler.scheduleAtFixedRate(this::sweepDeadConnections, 30, 30, TimeUnit.SECONDS);
    }

    @GetMapping(value = "/stream/{roomId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long roomId,
                             @RequestParam(required = false) Long sinceId,
                             HttpSession session) {
        String username = (String) session.getAttribute("username");
        SseEmitter emitter = new SseEmitter(0L);

        if (username == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("未登录"));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }

        final long[] lastId = { sinceId != null ? sinceId : 0L };
        final String user = username;
        final String key = user + ":" + roomId;

        // 同一用户同一房间只保留最新连接
        Connection previous = connections.remove(key);
        if (previous != null) {
            previous.close();
        }

        Runnable tick = () -> {
            Connection conn = connections.get(key);
            if (conn == null || conn.closed) {
                stop(key);
                return;
            }
            // 被踢 / 登出：会话失效后立即停止推送
            if (isSessionInvalid(session)
                    || session.getAttribute("username") == null
                    || !user.equals(session.getAttribute("username"))) {
                closeWithError(key, emitter, "会话已失效");
                return;
            }
            try {
                List<ChatMessage> msgs = chatService.getMessages(roomId, user,
                        lastId[0] > 0 ? lastId[0] : null);
                List<ChatMessage> payload;
                if (lastId[0] <= 0) {
                    payload = msgs;
                    if (!msgs.isEmpty()) {
                        lastId[0] = msgs.get(msgs.size() - 1).getId();
                    }
                } else {
                    payload = msgs.stream()
                            .filter(m -> m.getId() != null && m.getId() > lastId[0])
                            .toList();
                    if (!payload.isEmpty()) {
                        lastId[0] = payload.get(payload.size() - 1).getId();
                    }
                }
                if (!payload.isEmpty()) {
                    emitter.send(SseEmitter.event().name("messages").data(payload));
                } else {
                    emitter.send(SseEmitter.event().name("ping").data("1"));
                }
                if (conn != null) {
                    conn.lastActiveAt = System.currentTimeMillis();
                    conn.failures = 0;
                }
            } catch (Exception e) {
                // 发送失败即视为连接失效，立刻移除订阅
                closeWithError(key, emitter, null);
            }
        };

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(tick, 0, 2, TimeUnit.SECONDS);
        Connection conn = new Connection(emitter, future, user);
        connections.put(key, conn);

        Runnable done = () -> stop(key);
        emitter.onCompletion(done);
        emitter.onTimeout(done);
        emitter.onError(e -> done.run());
        return emitter;
    }

    private static boolean isSessionInvalid(HttpSession session) {
        if (session == null) {
            return true;
        }
        try {
            return session.getAttribute("username") == null;
        } catch (IllegalStateException ex) {
            return true;
        }
    }

    /** 登出/被踢时调用：立即关闭该用户全部 SSE 连接 */
    public void closeUserConnections(String username) {
        if (username == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Connection> e : connections.entrySet()) {
            if (username.equals(e.getValue().username)) {
                keys.add(e.getKey());
            }
        }
        for (String key : keys) {
            closeWithError(key, connections.get(key) != null ? connections.get(key).emitter : null, "会话已失效");
        }
    }

    private void closeWithError(String key, SseEmitter emitter, String message) {
        stop(key);
        if (emitter == null) {
            return;
        }
        try {
            if (message != null) {
                emitter.send(SseEmitter.event().name("error").data(message));
            }
        } catch (Exception ignored) {
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private void stop(String key) {
        Connection conn = connections.remove(key);
        if (conn != null) {
            conn.close();
        }
    }

    /** 心跳清理：移除长时间无响应或已关闭的连接 */
    private void sweepDeadConnections() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Connection> e : new ArrayList<>(connections.entrySet())) {
            Connection conn = e.getValue();
            if (conn.closed || (now - conn.lastActiveAt > 120_000L)) {
                closeWithError(e.getKey(), conn.emitter, null);
            }
        }
    }

    private static final class Connection {
        final SseEmitter emitter;
        final ScheduledFuture<?> future;
        final String username;
        volatile long lastActiveAt = System.currentTimeMillis();
        volatile int failures = 0;
        volatile boolean closed = false;

        Connection(SseEmitter emitter, ScheduledFuture<?> future, String username) {
            this.emitter = emitter;
            this.future = future;
            this.username = username;
        }

        void close() {
            closed = true;
            if (future != null) {
                future.cancel(true);
            }
        }
    }
}