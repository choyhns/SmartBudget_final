package com.smartbudget.rag.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.smartbudget.budget.BudgetMapper;
import com.smartbudget.rag.DefaultRagDocumentBuilder;
import com.smartbudget.rag.RagDocumentBuilder;
import com.smartbudget.rag.RagFactsMapper;

/**
 * RAG 설정. AI 호출은 Spring → Python만. Python AI Service가 Gemini API 사용.
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    /** RagDocumentBuilder 빈이 없을 때 DefaultRagDocumentBuilder 등록 (RagService 등에서 주입 보장) */
    @Bean
    @ConditionalOnMissingBean(RagDocumentBuilder.class)
    public RagDocumentBuilder ragDocumentBuilder(RagFactsMapper ragFactsMapper, BudgetMapper budgetMapper) {
        return new DefaultRagDocumentBuilder(ragFactsMapper, budgetMapper);
    }
}
