package com.bytemail.userservice.merchant;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemail.userservice.merchants.MerchantMapper;
import com.bytemail.userservice.merchants.MerchantShopRequest;
import com.bytemail.userservice.merchants.MerchantShopResponse;
import com.bytemail.userservice.user.entity.Merchant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantMapper merchantMapper;

    public MerchantShopResponse getMerchantShop(Long userId) {
        return toShopResponse(requireMerchant(userId));
    }

    public MerchantShopResponse updateMerchantShop(Long userId, MerchantShopRequest request) {
        Merchant merchant = requireMerchant(userId);
        if (request.shopName() != null) {
            merchant.setShopName(request.shopName());
        }
        if (request.logoUrl() != null) {
            merchant.setLogoUrl(request.logoUrl());
        }
        if (request.contactName() != null) {
            merchant.setContactName(request.contactName());
        }
        if (request.contactMobile() != null) {
            merchant.setContactMobile(request.contactMobile());
        }
        merchant.setUpdatedAt(LocalDateTime.now());
        merchantMapper.updateById(merchant);
        return toShopResponse(merchant);
    }

    private Merchant requireMerchant(Long userId) {
        Merchant merchant = merchantMapper.selectOne(new QueryWrapper<Merchant>().eq("owner_user_id", userId));
        if (merchant == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前用户不是商家");
        }
        return merchant;
    }

    private static MerchantShopResponse toShopResponse(Merchant merchant) {
        return new MerchantShopResponse(
                String.valueOf(merchant.getId()),
                merchant.getShopName(),
                merchant.getLogoUrl(),
                merchant.getContactName(),
                merchant.getContactMobile(),
                merchant.getStatus()
        );
    }
}
