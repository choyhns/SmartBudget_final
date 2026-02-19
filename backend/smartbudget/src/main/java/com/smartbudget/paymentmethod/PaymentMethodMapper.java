package com.smartbudget.paymentmethod;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMethodMapper {
    List<PaymentMethodDTO> selectAllPaymentMethods();
    PaymentMethodDTO selectPaymentMethodById(@Param("methodId") Long methodId);
    int insertPaymentMethod(PaymentMethodDTO method);
    int updatePaymentMethod(PaymentMethodDTO method);
    int deletePaymentMethod(@Param("methodId") Long methodId);
}
