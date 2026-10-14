package com.secondhand.mall.service;

import com.secondhand.mall.entity.WalletTransaction;
import com.secondhand.mall.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账单流水：由订单 + 充值记录派生，便于个人中心查看。
 * 充值流水落库（wallet_transactions），重启不丢失。
 */
@Service
public class WalletService {

    private final WalletTransactionRepository walletTransactionRepository;

    public WalletService(WalletTransactionRepository walletTransactionRepository) {
        this.walletTransactionRepository = walletTransactionRepository;
    }

    @Transactional
    public void addRecharge(String username, String amount) {
        BigDecimal amt = new BigDecimal(amount).setScale(2, java.math.RoundingMode.HALF_UP);
        add(username, "recharge", "余额充值", amt);
    }

    /** 累计充值金额（模拟充值上限校验用） */
    public BigDecimal sumRechargeAmount(String username) {
        BigDecimal sum = walletTransactionRepository.sumAmountByUsernameAndType(username, "recharge");
        return sum != null ? sum.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    /** 通用资金流水：buy/sell/refund/withdraw/recharge */
    @Transactional
    public void add(String username, String type, String title, BigDecimal amount) {
        if (username == null || username.isEmpty() || amount == null) return;
        WalletTransaction tx = new WalletTransaction(
                username, type, title, amount.setScale(2, java.math.RoundingMode.HALF_UP));
        walletTransactionRepository.save(tx);
    }

    /** 充值流水（新在前） */
    public List<Map<String, Object>> listRecharges(String username) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WalletTransaction tx : walletTransactionRepository.findByUsernameOrderByCreatedAtDesc(username)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", tx.getType());
            row.put("title", tx.getTitle());
            String amt = tx.getAmount() != null ? tx.getAmount().toPlainString() : "0";
            // 金额落库已带符号（出账为负）：正数补 +、负数保留 -，前端按符号着色
            String signed = amt.startsWith("-") || amt.startsWith("+") ? amt : "+" + amt;
            row.put("amount", signed);
            row.put("createdAt", String.valueOf(tx.getCreatedAt()));
            row.put("status", tx.getStatus());
            rows.add(row);
        }
        return rows;
    }

    /** 订单流水（收入/支出） */
    public List<Map<String, Object>> orderBills(String username,
                                               List<Map<String, Object>> buyOrders,
                                               List<Map<String, Object>> sellOrders) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> o : buyOrders) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "buy");
            row.put("title", "购买 · " + o.get("productName"));
            row.put("amount", "-" + o.get("amount"));
            row.put("createdAt", o.get("createdAt"));
            row.put("status", o.get("status"));
            rows.add(row);
        }
        for (Map<String, Object> o : sellOrders) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "sell");
            row.put("title", "售出 · " + o.get("productName"));
            row.put("amount", "+" + o.get("amount"));
            row.put("createdAt", o.get("createdAt"));
            row.put("status", o.get("status"));
            rows.add(row);
        }
        rows.sort((a, b) -> String.valueOf(b.get("createdAt")).compareTo(String.valueOf(a.get("createdAt"))));
        return rows;
    }

    @Transactional
    public void clearUser(String username) {
        walletTransactionRepository.deleteByUsername(username);
    }
}
