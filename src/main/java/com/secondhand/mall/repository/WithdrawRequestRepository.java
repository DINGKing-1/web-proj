package com.secondhand.mall.repository;

import com.secondhand.mall.entity.WithdrawRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawRequestRepository extends JpaRepository<WithdrawRequest, Long> {
    List<WithdrawRequest> findByUsernameOrderByCreatedAtDesc(String username);
    List<WithdrawRequest> findAllByOrderByCreatedAtDesc();
    List<WithdrawRequest> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
    long countByUsernameAndStatus(String username, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM WithdrawRequest r WHERE r.id = :id")
    Optional<WithdrawRequest> findByIdForUpdate(@Param("id") Long id);
}
