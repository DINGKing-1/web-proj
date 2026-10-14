package com.secondhand.mall.service;

import com.secondhand.mall.entity.Notification;
import com.secondhand.mall.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 站内通知。
 * push 不立即落库：先发应用事件，事务提交后（AFTER_COMMIT）再异步写库，
 * 事务回滚则不发通知，避免「下单失败仍收到成功通知」。
 * 无事务上下文时立即异步发送（兜底）。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public NotificationService(NotificationRepository notificationRepository,
                               org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
    }

    /** 通知载荷：事务提交后由监听器落库 */
    public record NotifyEvent(String username, String type, String title,
                              String content, String refType, String refId) {}

    /**
     * 入队通知。当前线程有事务则挂 AFTER_COMMIT；否则立即异步发送。
     * 事务回滚 → 事件不触发 → 不会误发。
     */
    public void push(String username, String type, String title, String content, String refType, String refId) {
        if (username == null || username.isEmpty()) return;
        eventPublisher.publishEvent(new NotifyEvent(username, type, title, content, refType, refId));
    }

    /** 事务提交后落库；无事务时 fallback 立即执行。回滚则不触发 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotify(NotifyEvent event) {
        try {
            Notification n = new Notification();
            n.setUsername(event.username());
            n.setType(event.type());
            n.setTitle(event.title());
            n.setContent(event.content());
            n.setRefType(event.refType());
            n.setRefId(event.refId());
            notificationRepository.save(n);
        } catch (Exception e) {
            log.warn("[Notify] 推送失败 user={} title={}: {}", event.username(), event.title(), e.getMessage());
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
