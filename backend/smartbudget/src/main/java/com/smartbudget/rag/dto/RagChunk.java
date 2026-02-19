package com.smartbudget.rag.dto;

import com.smartbudget.rag.model.RagMetadata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagChunk {
    private String id;
    private String text;
    private RagMetadata metadata;
    /** 검색 결과 유사도 점수 (1 = 완전 일치, cosine similarity 등) */
    private Double score;
}
