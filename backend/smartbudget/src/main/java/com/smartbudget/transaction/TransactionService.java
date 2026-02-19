package com.smartbudget.transaction;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartbudget.receipt.ReceiptMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TransactionService {
    
    @Autowired
    TransactionMapper transactionMapper;
    
    @Autowired(required = false)
    ReceiptMapper receiptMapper;

    public List<TransactionDTO> getAllTransactionsByUser(Long userId) throws Exception {
        return transactionMapper.selectAllTransactionsByUser(userId);
    }

    public TransactionDTO getTransactionById(Long txId) throws Exception {
        return transactionMapper.selectTransactionById(txId);
    }

    public List<TransactionDTO> getTransactionsByYearMonth(Long userId, String yearMonth) throws Exception {
        return transactionMapper.selectTransactionsByYearMonth(userId, yearMonth);
    }

    @Transactional
    public TransactionDTO createTransaction(TransactionDTO transaction) throws Exception {
        // categoryId가 null이면 로그 기록
        if (transaction.getCategoryId() == null) {
            log.warn("Transaction created without categoryId: merchant={}, userId={}", 
                transaction.getMerchant(), transaction.getUserId());
        }
        
        transactionMapper.insertTransaction(transaction);
        
        // receiptFileId가 있으면 receipt와 거래 연결
        Long receiptFileId = transaction.getReceiptFileId();
        if (receiptFileId != null && receiptMapper != null) {
            try {
                receiptMapper.linkReceiptToTransaction(receiptFileId, transaction.getTxId());
                log.info("Receipt {} linked to transaction {}", receiptFileId, transaction.getTxId());
            } catch (Exception e) {
                log.warn("Failed to link receipt {} to transaction {}: {}", 
                    receiptFileId, transaction.getTxId(), e.getMessage());
                // receipt 연결 실패해도 거래 생성은 계속 진행
            }
        }
        
        // INSERT 후 JOIN된 데이터를 가져와서 categoryName 포함하여 반환
        // 이렇게 하면 categoryName이 제대로 포함된 데이터를 반환할 수 있음
        return getTransactionById(transaction.getTxId());
    }

    public void updateTransaction(TransactionDTO transaction) throws Exception {
        transactionMapper.updateTransaction(transaction);
    }

    public void deleteTransaction(Long txId) throws Exception {
        transactionMapper.deleteTransaction(txId);
    }
}
