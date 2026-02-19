package com.smartbudget.category;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 모든 카테고리 조회 (플랫 리스트)
     */
    @GetMapping
    public List<CategoryDTO> getAllCategories() {
        return categoryService.getAllCategories();
    }

    /**
     * 카테고리 트리 구조로 조회
     */
    @GetMapping("/tree")
    public List<CategoryDTO> getCategoryTree() {
        return categoryService.getCategoryTree();
    }

    /**
     * 카테고리 생성
     */
    @PostMapping
    public CategoryDTO createCategory(@RequestBody CategoryDTO category) {
        return categoryService.createCategory(category);
    }

    /**
     * 카테고리 수정
     */
    @PutMapping("/{categoryId}")
    public CategoryDTO updateCategory(
            @PathVariable Long categoryId,
            @RequestBody CategoryDTO category) {
        return categoryService.updateCategory(categoryId, category);
    }

    /**
     * 카테고리 삭제
     */
    @DeleteMapping("/{categoryId}")
    public void deleteCategory(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
    }

    /**
     * 기본 카테고리 초기화
     */
    @PostMapping("/init")
    public void initDefaultCategories() {
        categoryService.initDefaultCategories();
    }
    
    /**
     * 텍스트 파일 기반 카테고리 초기화 (계층 구조)
     * 가계부 카테고리.txt 파일 내용을 기반으로 계층 구조 카테고리 생성
     * @param force 강제로 추가할지 여부 (기존 카테고리가 있어도 추가)
     */
    @PostMapping("/init-from-file")
    public void initCategoriesFromTextFile(@RequestParam(value = "force", defaultValue = "false") boolean force) {
        categoryService.initCategoriesFromTextFile(force);
    }
}
