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
    void testGetOrCreateRoom_DifferentProducts_ShouldBeSeparateRooms() {
        // 商品 A 的会话
        ChatRoom roomA = chatService.getOrCreateRoom("user1", "user2", 1L);
        assertEquals(1L, roomA.getProductId());

        // 同一买卖双方、不同商品 → 应为独立会话，不复用也不覆盖 productId
        ChatRoom roomB = chatService.getOrCreateRoom("user1", "user2", 2L);
        assertEquals(2L, roomB.getProductId());
        assertNotEquals(roomA.getId(), roomB.getId());

        // 同商品再次进入 → 复用同一会话
        ChatRoom again = chatService.getOrCreateRoom("user1", "user2", 1L);
        assertEquals(roomA.getId(), again.getId());
        assertEquals(1L, again.getProductId());

        assertEquals(2, chatRoomRepository.count());
    }

    @Test
    void testGetOrCreateRoom_ShopContext_IndependentOfProductRooms() {
        ChatRoom productRoom = chatService.getOrCreateRoom("user1", "user2", 1L);
        // 店铺页无商品上下文（productId 记 0），不应撞上商品会话
        ChatRoom shopRoom = chatService.getOrCreateRoom("user1", "user2", null);
        assertEquals(0L, shopRoom.getProductId());
        assertNotEquals(productRoom.getId(), shopRoom.getId());
    }
}
