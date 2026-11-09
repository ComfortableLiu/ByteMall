package com.bytemall.searchservice.search;

import com.bytemall.common.model.dto.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

// 标记这是一个Service
@Service
// 自动注入用来打日志的log变量
@Slf4j
// 自动注入依赖
@RequiredArgsConstructor
public class SearchService {
    public PageResult<String> products(String query, Integer page, Integer pageSize, String category, String brand, String minPrice, String maxPrice, String sort) {
        // 打日志
        log.info("Searching for {}", query);
        // TODO 查ES索引搜索
        return PageResult.of(new ArrayList<String>(), 0, page, pageSize);
    }

    public PageResult<String> suggestions(String q) {
        // 打日志
        log.info("Searching suggestions for {}", q);
        // TODO 查推荐选项
        return PageResult.of(new ArrayList<String>(), 0, 1, 10);
    }
}
