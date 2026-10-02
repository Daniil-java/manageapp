package com.kuklin.manageapp.payment.repositories;

import com.kuklin.manageapp.payment.entities.GenerationBalanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GenerationBalanceOperationRepository extends JpaRepository<GenerationBalanceOperation, Long> {
}
