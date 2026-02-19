package com.smartbudget.rag;

import java.util.List;

import com.smartbudget.rag.dto.FactsBuildResult;
import com.smartbudget.rag.dto.RagChunk;
import com.smartbudget.rag.model.RagMetadata;

/**
 * RAG 인덱싱용 문서(텍스트) 생성. 소비/예산/리포트 등에서 문단 단위 텍스트 수집.
 */
public interface RagDocumentBuilder {

    /**
     * userId, yearMonth 범위의 인덱싱할 문서(텍스트) 목록을 청크 형태로 반환.
     */
    List<RagChunk> buildDocuments(Long userId, String yearMonth);

    /**
     * Facts 블록 문자열 + 청크 목록 생성.
     * 총지출/총수입, 카테고리 TOP5, 예산·사용률, 예산 초과 카테고리, 전월 대비 증감 등.
     *
     * @param userId   사용자 ID
     * @param yearMonth YYYYMM
     * @return factsBlock + docList (첫 요소가 Facts 청크)
     */
    FactsBuildResult buildFacts(Long userId, String yearMonth);
}
