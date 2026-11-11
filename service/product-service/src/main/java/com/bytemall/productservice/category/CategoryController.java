package com.bytemall.productservice.category;


import com.bytemall.common.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<List<CategoryResponse.Item>>categoryList(){
        return ApiResponse.ok(categoryService.getCategoryList());
    }

}
