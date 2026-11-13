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


    @Select("""
             SELECT COUNT(*) from product_spu
            WHERE merchant_id = #{merchantId}
              AND (#{status,jdbcType=VARCHAR} IS NULL
                   OR status = #{status,jdbcType=VARCHAR})
            """)
    long countMerchantProducts(
            @Param("merchantId") long merchantId,
            @Param("status") String status
    );


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
                                WHERE merchant_id=#{merchantId}
                                AND (#{status,jdbcType=VARCHAR} IS NULL
                                OR status = #{status,jdbcType=VARCHAR})
                                ORDER BY id DESC
                                LIMIT #{limit} OFFSET #{offset}
        """)
    List<ProductSpu> selectMerchantProductPage(
            @Param("merchantId") long merchantId,
            @Param("limit") int limit,
            @Param("offset") long offset,
            @Param("status") String status
    );
}
