package com.smartbudget.receipt;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReceiptMapper {
    List<ReceiptFileDTO> selectReceiptsByUser(@Param("userId") Long userId);
    ReceiptFileDTO selectReceiptById(@Param("fileId") Long fileId);
    List<ReceiptFileDTO> selectReceiptsByStatus(@Param("userId") Long userId, @Param("status") String status);
    int insertReceipt(ReceiptFileDTO receipt);
    int updateReceipt(ReceiptFileDTO receipt);
    int updateReceiptStatus(@Param("fileId") Long fileId, @Param("status") String status);
    int linkReceiptToTransaction(@Param("fileId") Long fileId, @Param("txId") Long txId);
    int deleteReceipt(@Param("fileId") Long fileId);
}
