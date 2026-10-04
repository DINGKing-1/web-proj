package com.secondhand.mall.repository;

import com.secondhand.mall.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByUsernameOrderByCreatedAtDesc(String username);

    /** 用户清理：删除其全部钱包流水 */
    @Modifying
    @Query("DELETE FROM WalletTransaction w WHERE w.username = :username")
    void deleteByUsername(@Param("username") String username);
}
