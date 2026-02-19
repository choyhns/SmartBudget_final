package com.smartbudget.category;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CategoryMapper {
    List<CategoryDTO> selectAllCategories();
    List<CategoryDTO> selectRootCategories(); // 최상위 카테고리만
    List<CategoryDTO> selectChildCategories(@Param("parentId") Long parentId);
    CategoryDTO selectCategoryById(@Param("categoryId") Long categoryId);
    int insertCategory(CategoryDTO category);
    int updateCategory(CategoryDTO category);
    int deleteCategory(@Param("categoryId") Long categoryId);
}
