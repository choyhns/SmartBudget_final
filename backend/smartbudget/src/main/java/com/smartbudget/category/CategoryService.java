package com.smartbudget.category;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {
    
    @Autowired
    private CategoryMapper categoryMapper;
    
    /**
     * 모든 카테고리 조회 (플랫 리스트)
     */
    public List<CategoryDTO> getAllCategories() {
        return categoryMapper.selectAllCategories();
    }
    
    /**
     * 카테고리 트리 구조로 조회
     */
    public List<CategoryDTO> getCategoryTree() {
        List<CategoryDTO> all = categoryMapper.selectAllCategories();
        
        // parentId로 그룹핑
        Map<Long, List<CategoryDTO>> childrenMap = all.stream()
            .filter(c -> c.getParentId() != null)
            .collect(Collectors.groupingBy(CategoryDTO::getParentId));
        
        // 루트 카테고리에 자식 연결
        List<CategoryDTO> roots = all.stream()
            .filter(c -> c.getParentId() == null)
            .collect(Collectors.toList());
        
        roots.forEach(root -> buildTree(root, childrenMap));
        
        return roots;
    }
    
    private void buildTree(CategoryDTO parent, Map<Long, List<CategoryDTO>> childrenMap) {
        List<CategoryDTO> children = childrenMap.get(parent.getCategoryId());
        if (children != null) {
            parent.setChildren(children);
            children.forEach(child -> buildTree(child, childrenMap));
        }
    }
    
    /**
     * 카테고리 생성
     */
    public CategoryDTO createCategory(CategoryDTO category) {
        categoryMapper.insertCategory(category);
        return categoryMapper.selectCategoryById(category.getCategoryId());
    }
    
    /**
     * 카테고리 수정
     */
    public CategoryDTO updateCategory(Long categoryId, CategoryDTO category) {
        category.setCategoryId(categoryId);
        categoryMapper.updateCategory(category);
        return categoryMapper.selectCategoryById(categoryId);
    }
    
    /**
     * 카테고리 삭제
     */
    public void deleteCategory(Long categoryId) {
        categoryMapper.deleteCategory(categoryId);
    }
    
    /**
     * 텍스트 파일 기반 카테고리 초기화 (계층 구조)
     * 가계부 카테고리.txt 파일 내용을 기반으로 계층 구조 카테고리 생성
     */
    public void initCategoriesFromTextFile(boolean force) {
        List<CategoryDTO> existing = categoryMapper.selectAllCategories();
        if (!existing.isEmpty() && !force) {
            return; // 이미 카테고리가 있고 force가 false면 스킵
        }
        
        // 텍스트 파일 내용을 기반으로 한 카테고리 구조
        // 형식: {부모카테고리, 자식카테고리1, 자식카테고리2, ...}
        Object[][] categoryStructure = {
            {"주거", "관리비", "도시가스", "대출이자"},
            {"보험", "종합", "실비"},
            {"통신비", "휴대폰", "인터넷/TV", "수리/Acc"},
            {"식비", "식자재", "배달/외식", "간식/음료"},
            {"생활용품", "생활소모품", "가전/가구", "침구/인테리어"},
            {"꾸밈비", "의류/잡화", "미용/헤어", "세탁/수선"},
            {"건강", "병원/약국", "건강보조식품", "예방/검진"},
            {"자기계발", "운동", "도서"},
            {"자동차", "주유", "주차/통행료", "대중교통", "택시", "소모품/수리", "보험/세금", "세차"},
            {"여행", "국내여행", "해외여행"},
            {"문화", "OTT", "기타 입장료"},
            {"경조사", "가족/친척", "지인/동료"},
            {"기타", "세금", "회비", "기타"}
        };
        
        int addedCount = 0;
        for (Object[] categoryGroup : categoryStructure) {
            String parentName = (String) categoryGroup[0];
            
            // 부모 카테고리 찾기 또는 생성
            CategoryDTO parent = existing.stream()
                .filter(c -> c.getName().equals(parentName) && c.getParentId() == null)
                .findFirst()
                .orElse(null);
            
            if (parent == null) {
                parent = new CategoryDTO();
                parent.setName(parentName);
                categoryMapper.insertCategory(parent);
                addedCount++;
                existing.add(parent); // 기존 목록에 추가
            }
            
            // parent.getCategoryId()를 final 변수에 저장 (람다에서 사용하기 위해)
            final Long parentId = parent.getCategoryId();
            
            // 자식 카테고리 생성
            for (int i = 1; i < categoryGroup.length; i++) {
                String childName = (String) categoryGroup[i];
                
                // 이미 존재하는지 확인 (같은 부모를 가진 자식만)
                boolean exists = existing.stream()
                    .anyMatch(c -> c.getName().equals(childName) && 
                        parentId.equals(c.getParentId()));
                
                if (!exists) {
                    CategoryDTO child = new CategoryDTO();
                    child.setName(childName);
                    child.setParentId(parent.getCategoryId());
                    categoryMapper.insertCategory(child);
                    addedCount++;
                    existing.add(child); // 기존 목록에 추가
                }
            }
        }
    }
    
    /**
     * 기본 카테고리 초기화
     */
    public void initDefaultCategories() {
        List<CategoryDTO> existing = categoryMapper.selectAllCategories();
        if (!existing.isEmpty()) {
            return; // 이미 카테고리가 있으면 스킵
        }
        
        // 기본 카테고리 생성
        String[][] defaultCategories = {
            {"식비", null},
            {"교통", null},
            {"쇼핑", null},
            {"생활", null},
            {"의료/건강", null},
            {"문화/여가", null},
            {"교육", null},
            {"경조사", null},
            {"저축/투자", null},
            {"기타", null}
        };
        
        for (String[] cat : defaultCategories) {
            CategoryDTO category = new CategoryDTO();
            category.setName(cat[0]);
            categoryMapper.insertCategory(category);
        }
    }
}
