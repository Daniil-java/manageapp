package com.kuklin.manageapp.payment.services;

import com.kuklin.manageapp.payment.entities.PaymentFailedLog;
import com.kuklin.manageapp.payment.repositories.PaymentFailedLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentFailedLogService {
    private final PaymentFailedLogRepository paymentFailedLogRepository;

    public void createLog(Long paymentId, String error) {
        paymentFailedLogRepository.save(
                new PaymentFailedLog()
                        .setPaymentId(paymentId)
                        .setErrorMessage(error)
                        .setStatus(PaymentFailedLog.Status.PENDING)
        );
    }

    public List<PaymentFailedLog> getAllLogs() {
        return paymentFailedLogRepository.findAll();
    }
}
