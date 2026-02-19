package com.smartbudget.recommendation;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RecommendationMapper {
    // 카드
    List<CardDTO> selectAllCards();
    CardDTO selectCardById(@Param("cardId") Long cardId);
    int insertCard(CardDTO card);
    int updateCardImage(@Param("cardId") Long cardId, @Param("imageUrl") String imageUrl);
    /** 카드명·사명으로 일치하는 행의 link만 갱신 (체크/신용 JSON 동기화용) */
    int updateCardLink(@Param("name") String name, @Param("company") String company, @Param("link") String link);
    /** cardId로 link만 갱신 */
    int updateCardLinkById(@Param("cardId") Long cardId, @Param("link") String link);

    // 금융상품
    List<ProductDTO> selectAllProducts();
    List<ProductDTO> selectProductsByType(@Param("type") String type);
    ProductDTO selectProductById(@Param("productId") Long productId);
    int insertProduct(ProductDTO product);
    
    // 사용자 카드
    List<CardDTO> selectUserCards(@Param("userId") Long userId);
    int insertUserCard(@Param("userId") Long userId, @Param("cardId") Long cardId);
    int deleteUserCard(@Param("userId") Long userId, @Param("cardId") Long cardId);
    
    // 추천 내역
    List<RecommendationDTO> selectRecommendationsByUser(@Param("userId") Long userId);
    List<RecommendationDTO> selectRecommendationsByYearMonth(@Param("userId") Long userId, @Param("yearMonth") String yearMonth);
    int insertRecommendation(RecommendationDTO recommendation);
    int deleteOldRecommendations(@Param("userId") Long userId, @Param("yearMonth") String yearMonth);
}
