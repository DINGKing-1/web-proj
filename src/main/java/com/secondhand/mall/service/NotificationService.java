package com.secondhand.mall.service;

import com.secondhand.mall.entity.Notification;
import com.secondhand.mall.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 站内通知。push 走异步线程池，避免下单/支付等主流程被写库阻塞。
 * 多节点场景可将 push 改为投递 MQ（RabbitMQ/Kafka），由消费者落库。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Async("mallAsyncExecutor")
    public void push(String username, String type, String title, String content, String refType, String refId) {
        if (username == null || username.isEmpty()) return;
        try {
            Notification n = new Notification();
            n.setUsername(username);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setRefType(refType);
            n.setRefId(refId);
            notificationRepository.save(n);
        } catch (Exception e) {
            log.warn("[Notify] 异步推送失败 user={} title={}: {}", username, title, e.getMessage());
        }
    }

    public List<Notification> list(String username) {
        return notificationRepository.findTop50ByUsernameOrderByCreatedAtDesc(username);
    }

    public long unreadCount(String username) {
        return notificationRepository.countByUsernameAndReadFlagFalse(username);
    }

    @Transactional
    public int markAllRead(String username) {
        return notificationRepository.markAllRead(username);
    }

    @Transactional
    public void markRead(String username, Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUsername().equals(username)) {
                n.setReadFlag(true);
                notificationRepository.save(n);
            }
        });
    }
}
