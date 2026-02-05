package com.kuklin.manageapp.bots.payment.repositories;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GenerationBalanceOperationRepository extends JpaRepository<GenerationBalanceOperation, Long> {
}
