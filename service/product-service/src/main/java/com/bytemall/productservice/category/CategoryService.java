package com.bytemall.productservice.category;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bytemall.productservice.entity.Category;
import com.bytemall.productservice.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryMapper categoryMapper;

    public List<CategoryResponse.Item> getCategoryList() {

        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                        .eq(Category::getEnabled, true))
                .stream()
                .map(category -> {
                    Long parentId = category.getParentId();
                    return new CategoryResponse.Item(
                            category.getId().toString(),
                            parentId == null ? null : parentId.toString(),
                            category.getName(),
                            category.getLevel()
                    );
                }).toList();
    }
}
