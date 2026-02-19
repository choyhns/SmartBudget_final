package com.smartbudget.rag.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagMetadata {
    private Long userId;
    private String yearMonth;
    private String docType;
    /** 참조 ID 목록 (예: reportId, txIds) */
    private List<String> refIds;
}
