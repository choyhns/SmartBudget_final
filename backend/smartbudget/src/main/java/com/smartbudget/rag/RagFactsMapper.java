package com.smartbudget.rag;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * RAG Facts 블록용 SQL 집계. 통계/집계는 DB에서 수행하고 LLM에는 결과만 전달.
 */
@Mapper
public interface RagFactsMapper {

    /**
     * 해당 사용자·연월의 소비/수입 집계 1행 (SQL 집계).
     */
    RagFactsDTO.SelectSpendingRow selectSpendingByYearMonth(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth);

    /**
     * 카테고리별 지출 합계 (amount < 0, SQL 집계).
     */
    List<RagFactsDTO.CategoryAmountRow> selectCategoryExpensesByYearMonth(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth);
}
