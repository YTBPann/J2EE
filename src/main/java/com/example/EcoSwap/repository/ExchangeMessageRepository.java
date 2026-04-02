package com.example.EcoSwap.repository;

import com.example.EcoSwap.entity.ExchangeMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExchangeMessageRepository extends JpaRepository<ExchangeMessage, Long> {
    List<ExchangeMessage> findByExchangeRequestIdOrderByCreatedAtAsc(Long exchangeRequestId);
}
