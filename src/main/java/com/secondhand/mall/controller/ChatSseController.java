package com.secondhand.mall.controller;

import com.secondhand.mall.entity.ChatMessage;
import com.secondhand.mall.service.ChatService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 聊天实时推送（SSE）。不改动既有 REST 聊天 API。
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
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public ChatSseController(ChatService chatService) {
        this.chatService = chatService;
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

        Runnable tick = () -> {
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
            } catch (Exception e) {
                stop(key);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        };

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(tick, 0, 2, TimeUnit.SECONDS);
        tasks.put(key, future);

        Runnable done = () -> stop(key);
        emitter.onCompletion(done);
        emitter.onTimeout(done);
        emitter.onError(e -> done.run());
        return emitter;
    }

    private void stop(String key) {
        ScheduledFuture<?> f = tasks.remove(key);
        if (f != null) {
            f.cancel(true);
        }
    }
}
