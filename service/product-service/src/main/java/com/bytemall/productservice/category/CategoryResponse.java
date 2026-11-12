package com.bytemall.productservice.category;

public final class CategoryResponse {
    private  CategoryResponse() { }
    public record  Item(
            String categoryId,
            String parentId,
            String name,
            Integer level
    ){}

}
