package com.secondhand.mall.controller;

import com.secondhand.mall.entity.ChatMessage;
import com.secondhand.mall.entity.ChatRoom;
import com.secondhand.mall.entity.QuickReply;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.exception.UnauthorizedException;
import com.secondhand.mall.service.ChatService;
import com.secondhand.mall.service.QuickReplyService;
import com.secondhand.mall.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 在线客服：用户咨询 admin，后台接待 + 快捷话术
 */
@RestController
@RequestMapping("/api")
public class SupportController extends BaseApiController {

    /** 客服账号（平台管理员） */
    public static final String SUPPORT_AGENT = "admin";

    private final ChatService chatService;
    private final QuickReplyService quickReplyService;
    private final UserService userService;

    public SupportController(ChatService chatService, QuickReplyService quickReplyService, UserService userService) {
        this.chatService = chatService;
        this.quickReplyService = quickReplyService;
        this.userService = userService;
    }

    /** 用户进入客服：获取/创建与 admin 的会话 */
    @PostMapping("/support/session")
    public Map<String, Object> openSupport(HttpSession session) {
        try {
            String username = getLoginUsername(session);
            if (SUPPORT_AGENT.equals(username)) {
                return fail("请从管理后台进入客服工作台").toMap();
            }
            ChatRoom room = chatService.getOrCreateSupportRoom(username, SUPPORT_AGENT);
            User agent = userService.findByUsername(SUPPORT_AGENT);
            return ok("获取成功")
                    .put("roomId", room.getId())
                    .put("agentName", agent.getNickname() != null ? agent.getNickname() : "平台客服")
                    .put("agentAvatar", agent.getAvatar())
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** FAQ 快捷问题（标题） */
    @GetMapping("/support/faq")
    public Map<String, Object> faq() {
        List<QuickReply> list = quickReplyService.list();
        return ok("获取成功").put("items", list).toMap();
    }

    /**
     * 用户点击常见问题：记录用户提问，并由客服自动回复预设内容。
     * 解决「只发出问题、设定好的回答发不过来」的缺口。
     */
    @PostMapping("/support/ask")
    public Map<String, Object> askFaq(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            if (SUPPORT_AGENT.equals(username)) {
                return fail("请从管理后台进入客服工作台").toMap();
            }
            Long roomId = Long.parseLong(request.get("roomId").toString());
            Long faqId = request.get("faqId") != null
                    ? Long.parseLong(request.get("faqId").toString()) : null;
            String title = request.get("title") != null ? String.valueOf(request.get("title")) : null;

            QuickReply item = faqId != null ? quickReplyService.get(faqId) : quickReplyService.findByTitle(title);
            if (item == null) {
                return fail("该常见问题不存在或已删除").toMap();
            }

            // 1) 用户提问（以问题标题作为消息）
            ChatMessage question = chatService.sendMessage(roomId, username, item.getTitle(), "text", null);
            // 2) 客服自动回复预设答案
            ChatMessage answer = chatService.sendMessage(roomId, SUPPORT_AGENT, item.getContent(), "text", null);

            return ok("发送成功")
                    .put("messages", java.util.List.of(question, answer))
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 管理端：客服会话列表 */
    @GetMapping("/admin/support/rooms")
    public Map<String, Object> supportRooms(HttpSession session) {
        try {
            String username = getLoginUsername(session);
            if (!SUPPORT_AGENT.equals(username) && !"admin".equals(userService.findByUsername(username).getRole())) {
                return fail("无客服权限").toMap();
            }
            List<Map<String, Object>> rooms = chatService.getSupportRoomsForAdmin(SUPPORT_AGENT);
            return ok("获取成功").put("rooms", rooms).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 管理端：话术库 */
    private void checkSupportAdmin(HttpSession session) {
        String username = getLoginUsername(session);
        if (!SUPPORT_AGENT.equals(username) && !"admin".equals(userService.findByUsername(username).getRole())) {
            throw new UnauthorizedException("无客服权限");
        }
    }

    @GetMapping("/admin/support/replies")
    public Map<String, Object> listReplies(HttpSession session) {
        checkSupportAdmin(session);
        return ok("获取成功").put("items", quickReplyService.list()).toMap();
    }

    @PostMapping("/admin/support/replies")
    public Map<String, Object> createReply(@RequestBody QuickReply body, HttpSession session) {
        checkSupportAdmin(session);
        try {
            return ok("已添加").put("item", quickReplyService.create(body)).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PutMapping("/admin/support/replies/{id}")
    public Map<String, Object> updateReply(@PathVariable Long id, @RequestBody QuickReply body, HttpSession session) {
        checkSupportAdmin(session);
        try {
            return ok("已更新").put("item", quickReplyService.update(id, body)).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @DeleteMapping("/admin/support/replies/{id}")
    public Map<String, Object> deleteReply(@PathVariable Long id, HttpSession session) {
        checkSupportAdmin(session);
        try {
            quickReplyService.delete(id);
            return ok("已删除").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 管理端拉取消息（与用户聊天共用接口，这里放便捷别名） */
    @GetMapping("/admin/support/messages/{roomId}")
    public Map<String, Object> supportMessages(@PathVariable Long roomId,
                                               @RequestParam(required = false) Long sinceId,
                                               HttpSession session) {
        try {
            String username = getLoginUsername(session);
            List<ChatMessage> messages = chatService.getMessages(roomId, username, sinceId);
            chatService.markAsRead(roomId, username);
            return ok("获取成功").put("messages", messages).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PostMapping("/admin/support/message")
    public Map<String, Object> supportReply(@RequestBody Map<String, Object> request, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Long roomId = Long.parseLong(request.get("roomId").toString());
            String content = String.valueOf(request.get("content"));
            String type = request.get("type") != null ? String.valueOf(request.get("type")) : "text";
            ChatMessage msg = chatService.sendMessage(roomId, username, content, type, null);
            return ok("发送成功").put("messageId", msg.getId()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }
}
