package com.kuklin.manageapp.bots.payment.repositories;

import com.kuklin.manageapp.bots.payment.entities.PaymentFailedLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentFailedLogRepository extends JpaRepository<PaymentFailedLog, Long> {
}
