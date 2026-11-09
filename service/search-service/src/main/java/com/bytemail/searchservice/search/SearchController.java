package com.bytemail.searchservice.search;

import com.bytemail.common.model.dto.ApiResponse;
import com.bytemail.common.model.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 标记这是个Controller
@RestController
// 自动注入依赖
@RequiredArgsConstructor
// 标记这里面的所有接口都是/search开头的
@RequestMapping("/search")
public class SearchController {
    // 自动注入SearchService
    // 因为有了@RequiredArgsConstructor，所以这里不需要new或者其他操作了，编译器自动就会new一个新对象
    private final SearchService searchService;

    // GET 接口
    @GetMapping("/products")
    // 搜索主接口
    public ApiResponse<PageResult<String>> products(
            @RequestParam String q,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "brand", required = false) String brand,
            @RequestParam(value = "minPrice", required = false) String minPrice,
            @RequestParam(value = "maxPrice", required = false) String maxPrice,
            @RequestParam(value = "sort", required = false) String sort
    ) {
        return ApiResponse.ok(searchService.products(q, page, pageSize, category, brand, minPrice, maxPrice, sort));
    }

    // GET 接口
    @GetMapping("/suggestions")
    // 搜索主接口
    public ApiResponse<PageResult<String>> suggestions(@RequestParam String q) {
        return ApiResponse.ok(searchService.suggestions(q));
    }
}
