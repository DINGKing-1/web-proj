package com.secondhand.mall.service;

import com.secondhand.mall.entity.ChatRoom;
import com.secondhand.mall.repository.ChatRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @BeforeEach
    void setUp() {
        chatRoomRepository.deleteAll();
    }

    @Test
    void testGetOrCreateRoom_ExistingRoom_ShouldNotOverrideProductId() {
        // 创建聊天室，关联商品 A
        ChatRoom room = chatService.getOrCreateRoom("user1", "user2", 1L);
        assertEquals(1L, room.getProductId());

        // 再次获取，关联商品 B
        ChatRoom sameRoom = chatService.getOrCreateRoom("user1", "user2", 2L);

        // 验证 productId 未被覆盖
        assertEquals(1L, sameRoom.getProductId());
        assertEquals(room.getId(), sameRoom.getId());
    }
}
