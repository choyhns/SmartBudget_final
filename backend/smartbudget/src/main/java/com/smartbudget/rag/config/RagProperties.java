package com.smartbudget.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private Embed embed = new Embed();
    private Pgvector pgvector = new Pgvector();
    private int topK = 5;
    private Scheduler scheduler = new Scheduler();

    /** LLM/임베딩은 Spring에서 직접 호출하지 않음. Python AI Service가 Gemini API 사용. */
    @Data
    public static class Embed {
        private String model = "text-embedding-004";
        /** 임베딩 차원 (pgvector vector(N) 및 인덱스용) */
        private int dimension = 768;
    }

    @Data
    public static class Pgvector {
        /** true: pgvector 유사도 검색, false: content ILIKE fallback */
        private boolean enabled = true;
    }

    /** true: embedding을 JSONB로 저장·검색 (pgvector 미사용, Railway 등). false: pgvector 사용 */
    private boolean useJsonb = true;

    /** true: 벡터 저장소로 Chroma 사용 (ml-server). useChroma=true면 PostgreSQL에는 content만 저장. */
    private boolean useChroma = true;

    @Data
    public static class Scheduler {
        private boolean enabled = false;
        private String cron = "0 0 3 * * ?";
        private String zone = "Asia/Seoul";
    }
}
