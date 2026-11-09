package com.bytemail.common.model.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 分页结果
 */
@Data
public class PageResult<T> {

    private List<T> list = Collections.emptyList();

    private long total;

    private long page;

    private long pageSize;

    public static <T> PageResult<T> of(List<T> list, long total, long page, long pageSize) {
        PageResult<T> r = new PageResult<>();
        r.list = list;
        r.total = total;
        r.page = page;
        r.pageSize = pageSize;
        return r;
    }
}
