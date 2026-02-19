package com.smartbudget.paymentmethod;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

    @Autowired
    private PaymentMethodMapper paymentMethodMapper;

    /**
     * 모든 결제수단 조회
     */
    @GetMapping
    public List<PaymentMethodDTO> getAllPaymentMethods() {
        return paymentMethodMapper.selectAllPaymentMethods();
    }

    /**
     * 결제수단 생성
     */
    @PostMapping
    public PaymentMethodDTO createPaymentMethod(@RequestBody PaymentMethodDTO method) {
        paymentMethodMapper.insertPaymentMethod(method);
        return paymentMethodMapper.selectPaymentMethodById(method.getMethodId());
    }

    /**
     * 결제수단 수정
     */
    @PutMapping("/{methodId}")
    public PaymentMethodDTO updatePaymentMethod(
            @PathVariable Long methodId,
            @RequestBody PaymentMethodDTO method) {
        method.setMethodId(methodId);
        paymentMethodMapper.updatePaymentMethod(method);
        return paymentMethodMapper.selectPaymentMethodById(methodId);
    }

    /**
     * 결제수단 삭제
     */
    @DeleteMapping("/{methodId}")
    public void deletePaymentMethod(@PathVariable Long methodId) {
        paymentMethodMapper.deletePaymentMethod(methodId);
    }
}
