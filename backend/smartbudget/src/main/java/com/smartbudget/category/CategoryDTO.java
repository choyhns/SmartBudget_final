package com.smartbudget.category;

import lombok.Data;
import java.util.List;

@Data
public class CategoryDTO {
    private Long categoryId;
    private String name;
    private Long parentId;
    
    // 하위 카테고리 (트리 구조용)
    private List<CategoryDTO> children;
}
