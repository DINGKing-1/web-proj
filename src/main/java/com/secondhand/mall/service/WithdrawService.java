package com.secondhand.mall.service;

import com.secondhand.mall.aspect.Audited;

import com.secondhand.mall.entity.User;
import com.secondhand.mall.entity.WalletTransaction;
import com.secondhand.mall.entity.WithdrawRequest;
import com.secondhand.mall.repository.UserRepository;
import com.secondhand.mall.repository.WalletTransactionRepository;
import com.secondhand.mall.repository.WithdrawRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 卖家提现：申请时冻结余额，审核通过打款、拒绝则退回。
 */
@Service
public class WithdrawService {

    private static final BigDecimal MIN_AMOUNT = new BigDecimal("10.00");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("50000.00");

    private final WithdrawRequestRepository withdrawRequestRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public WithdrawService(WithdrawRequestRepository withdrawRequestRepository,
                           UserRepository userRepository,
                           WalletTransactionRepository walletTransactionRepository) {
        this.withdrawRequestRepository = withdrawRequestRepository;
        this.userRepository = userRepository;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    @Transactional
    public WithdrawRequest apply(String username, BigDecimal amount, String account) {
        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0) {
            throw new RuntimeException("提现金额不能低于 ¥" + MIN_AMOUNT.toPlainString());
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new RuntimeException("单次提现不能超过 ¥" + MAX_AMOUNT.toPlainString());
        }
        if (account == null || account.trim().isEmpty()) {
            throw new RuntimeException("请填写收款账号");
        }
        // 同一用户仅允许一笔待处理提现（行锁防并发双提）
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (withdrawRequestRepository.countByUsernameAndStatus(username, "pending") > 0) {
            throw new RuntimeException("已有一笔待处理的提现，请等待审核");
        }
        if (user.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("余额不足，当前余额 ¥" + user.getBalance().toPlainString());
        }

        // 申请即冻结（扣余额）
        user.setBalance(user.getBalance().subtract(amount));
        userRepository.save(user);

        WithdrawRequest req = new WithdrawRequest(username, amount.setScale(2, java.math.RoundingMode.HALF_UP), account.trim());
        req = withdrawRequestRepository.save(req);

        walletTransactionRepository.save(new WalletTransaction(
                username, "withdraw", "提现申请（冻结）", amount.negate()));
        return req;
    }

    public List<WithdrawRequest> listByUser(String username) {
        return withdrawRequestRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public List<WithdrawRequest> listAll() {
        return withdrawRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    public long pendingCount() {
        return withdrawRequestRepository.countByStatus("pending");
    }

    @Transactional
    @Audited(action = "提现审批通过", targetType = "withdraw")
    public WithdrawRequest approve(Long id, String adminUsername) {
        // 行锁 + 状态复核，防双管理员并发「通过+驳回」
        WithdrawRequest req = withdrawRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("提现单不存在"));
        if (!"pending".equals(req.getStatus())) {
            throw new RuntimeException("该提现单已处理");
        }
        req.setStatus("approved");
        req.setProcessedBy(adminUsername);
        req.setProcessedAt(LocalDateTime.now());
        return withdrawRequestRepository.save(req);
    }

    @Transactional
    @Audited(action = "提现驳回", targetType = "withdraw")
    public WithdrawRequest reject(Long id, String adminUsername, String reason) {
        WithdrawRequest req = withdrawRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("提现单不存在"));
        if (!"pending".equals(req.getStatus())) {
            throw new RuntimeException("该提现单已处理");
        }
        // 退回余额
        User user = userRepository.findByUsernameForUpdate(req.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setBalance(user.getBalance().add(req.getAmount()));
        userRepository.save(user);

        req.setStatus("rejected");
        req.setRejectReason(reason != null && !reason.isBlank() ? reason.trim() : "审核未通过");
        req.setProcessedBy(adminUsername);
        req.setProcessedAt(LocalDateTime.now());
        WithdrawRequest saved = withdrawRequestRepository.save(req);

        walletTransactionRepository.save(new WalletTransaction(
                req.getUsername(), "refund", "提现退回", req.getAmount()));
        return saved;
    }
}
