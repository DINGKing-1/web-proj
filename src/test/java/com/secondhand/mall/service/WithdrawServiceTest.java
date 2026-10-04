package com.secondhand.mall.service;

import com.secondhand.mall.entity.User;
import com.secondhand.mall.entity.WithdrawRequest;
import com.secondhand.mall.repository.UserRepository;
import com.secondhand.mall.repository.WalletTransactionRepository;
import com.secondhand.mall.repository.WithdrawRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WithdrawServiceTest {

    @Autowired
    private WithdrawService withdrawService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WithdrawRequestRepository withdrawRequestRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @BeforeEach
    void setUp() {
        walletTransactionRepository.deleteAll();
        withdrawRequestRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createUser(String username, double balance) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("pwd");
        u.setEmail(username + "@t.com");
        u.setPhone("13800000000");
        u.setNickname(username);
        u.setBalance(BigDecimal.valueOf(balance));
        return userRepository.save(u);
    }

    @Test
    void testApply_FreezesBalance() {
        createUser("seller1", 1000);
        WithdrawRequest req = withdrawService.apply("seller1", new BigDecimal("100"), "alipay:123");

        assertEquals("pending", req.getStatus());
        User after = userRepository.findByUsername("seller1").orElseThrow();
        assertEquals(0, new BigDecimal("900").compareTo(after.getBalance()), "申请即冻结余额");
    }

    @Test
    void testApply_AmountBounds() {
        createUser("seller1", 1000);
        assertThrows(RuntimeException.class,
                () -> withdrawService.apply("seller1", new BigDecimal("5"), "acc"));
        assertThrows(RuntimeException.class,
                () -> withdrawService.apply("seller1", new BigDecimal("999999"), "acc"));
        assertThrows(RuntimeException.class,
                () -> withdrawService.apply("seller1", new BigDecimal("100"), "  "));
    }

    @Test
    void testApply_OnlyOnePending() {
        createUser("seller1", 1000);
        withdrawService.apply("seller1", new BigDecimal("100"), "acc1");
        assertThrows(RuntimeException.class,
                () -> withdrawService.apply("seller1", new BigDecimal("50"), "acc2"));
    }

    @Test
    void testApprove_KeepsFrozen() {
        createUser("seller1", 1000);
        WithdrawRequest req = withdrawService.apply("seller1", new BigDecimal("200"), "acc");

        WithdrawRequest approved = withdrawService.approve(req.getId(), "admin");
        assertEquals("approved", approved.getStatus());
        assertEquals("admin", approved.getProcessedBy());

        User after = userRepository.findByUsername("seller1").orElseThrow();
        assertEquals(0, new BigDecimal("800").compareTo(after.getBalance()), "通过后余额不退回（已打款）");
    }

    @Test
    void testReject_RefundsBalance() {
        createUser("seller1", 1000);
        WithdrawRequest req = withdrawService.apply("seller1", new BigDecimal("300"), "acc");

        WithdrawRequest rejected = withdrawService.reject(req.getId(), "admin", "资料不符");
        assertEquals("rejected", rejected.getStatus());

        User after = userRepository.findByUsername("seller1").orElseThrow();
        assertEquals(0, new BigDecimal("1000").compareTo(after.getBalance()), "驳回须全额退回");
    }

    @Test
    void testDoubleProcess_ShouldThrow() {
        createUser("seller1", 1000);
        WithdrawRequest req = withdrawService.apply("seller1", new BigDecimal("100"), "acc");
        withdrawService.approve(req.getId(), "admin");
        assertThrows(RuntimeException.class, () -> withdrawService.approve(req.getId(), "admin"));
        assertThrows(RuntimeException.class, () -> withdrawService.reject(req.getId(), "admin", "x"));
    }
}
