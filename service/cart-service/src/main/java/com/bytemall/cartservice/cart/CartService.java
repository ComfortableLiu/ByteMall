package com.bytemall.cartservice.cart;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

@Service
public class CartService {

    public CartResponses.Cart getCart(Long userId) {
        CartResponses.Cart cart = new CartResponses.Cart(
                new ArrayList<>(),
                userId,
                123
        );
        return cart;
        // TODO 按userId读取Redis购物车，并通过商品与库存服务补齐实时快照。
//        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "查询购物车待实现：Redis与商品快照查询尚未接入");
    }

    public CartResponses.Cart addItem(Long userId, CartRequests.AddItem request) {
        // TODO 校验商品与库存后写入Redis；已有SKU需合并数量并限制总量不超过999。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "添加购物车商品待实现：Redis写入与商品校验尚未接入");
    }

    public CartResponses.Cart updateItem(
            Long userId,
            String skuId,
            CartRequests.UpdateItem request
    ) {
        // TODO 仅更新请求中提供的数量或选中状态，并重新计算购物车小计。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "更新购物车商品待实现：Redis更新逻辑尚未接入");
    }

    public void deleteItem(Long userId, String skuId) {
        // TODO 从当前用户的Redis购物车删除指定SKU，保持幂等删除语义。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "删除购物车商品待实现：Redis删除逻辑尚未接入");
    }

    public CartResponses.Cart updateSelection(Long userId, CartRequests.UpdateSelection request) {
        // TODO 批量更新指定SKU的选中状态，并返回最新购物车。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "批量更新购物车选中状态待实现：Redis更新逻辑尚未接入");
    }
}
