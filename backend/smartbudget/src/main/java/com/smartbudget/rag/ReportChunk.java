package com.smartbudget.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportChunk {
    private long chunkId;
    private long reportId;
    private long userId;
    private String yearMonth;
    private int chunkIndex;
    private String content;
    /** 메타데이터 필터용: summary, stats, category_expenses, chunk 등 */
    private String docType;
}
