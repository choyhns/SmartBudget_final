package com.smartbudget.transaction;

import java.util.List;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public List<TransactionDTO> getAllTransactions(@RequestParam Long userId) throws Exception {
        return transactionService.getAllTransactionsByUser(userId);
    }

    @GetMapping("/{txId}")
    public TransactionDTO getTransactionById(@PathVariable Long txId) throws Exception {
        return transactionService.getTransactionById(txId);
    }

    @GetMapping("/month")
    public List<TransactionDTO> getTransactionsByYearMonth(
            @RequestParam Long userId,
            @RequestParam String yearMonth) throws Exception {
        return transactionService.getTransactionsByYearMonth(userId, yearMonth);
    }

    @PostMapping
    public TransactionDTO createTransaction(@RequestBody TransactionDTO transaction) throws Exception {
        return transactionService.createTransaction(transaction);
    }

    @PutMapping("/{txId}")
    public TransactionDTO updateTransaction(@PathVariable Long txId, @RequestBody TransactionDTO transaction) throws Exception {
        transaction.setTxId(txId);
        transactionService.updateTransaction(transaction);
        return transactionService.getTransactionById(txId);
    }

    @DeleteMapping("/{txId}")
    public void deleteTransaction(@PathVariable Long txId) throws Exception {
        transactionService.deleteTransaction(txId);
    }
}
