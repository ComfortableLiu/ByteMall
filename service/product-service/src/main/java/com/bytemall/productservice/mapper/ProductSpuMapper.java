package com.bytemall.productservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.productservice.entity.ProductSpu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductSpuMapper extends BaseMapper<ProductSpu> {
    @Select("""
            SELECT COUNT(*)
            FROM product_spu
            WHERE status = 'ON_SALE'
            """)
    long countOnSale();

    @Select("""
            SELECT id,
                   merchant_id AS merchantId,
                   category_id AS categoryId,
                   title,
                   subtitle,
                   brand,
                   status,
                   sales_count AS salesCount,
                   published_at AS publishedAt
            FROM product_spu
                        WHERE status = 'ON_SALE'
                        ORDER BY id DESC
                        LIMIT #{limit} OFFSET #{offset}
            """)
    List<ProductSpu> selectOnSalePage(
            @Param("limit") int limit,
            @Param("offset") long offset
    );
}
