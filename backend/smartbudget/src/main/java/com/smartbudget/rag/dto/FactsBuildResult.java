package com.smartbudget.rag.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RagDocumentBuilder.buildFacts 결과: Facts 블록 문자열 + 청크 목록.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FactsBuildResult {
    /** Facts 블록 전체 텍스트 (총지출/수입, TOP5, 예산, 초과, 전월대비 등) */
    private String factsBlock;
    /** 청크 목록 (첫 요소가 Facts 청크) */
    private List<RagChunk> docList;
}
