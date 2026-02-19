package com.smartbudget.transaction;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TransactionMapper {
    List<TransactionDTO> selectAllTransactionsByUser(@Param("userId") Long userId) throws Exception;
    TransactionDTO selectTransactionById(@Param("txId") Long txId) throws Exception;
    List<TransactionDTO> selectTransactionsByYearMonth(@Param("userId") Long userId, @Param("yearMonth") String yearMonth) throws Exception;
    List<TransactionDTO> selectTransactionsByDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    ) throws Exception;
    int insertTransaction(TransactionDTO transaction) throws Exception;
    int updateTransaction(TransactionDTO transaction) throws Exception;
    int deleteTransaction(@Param("txId") Long txId) throws Exception;
}
