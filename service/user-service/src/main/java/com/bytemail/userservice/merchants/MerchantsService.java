package com.bytemail.userservice.merchants;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemail.userservice.user.entity.Merchant;
import com.bytemail.userservice.user.entity.MerchantApplication;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MerchantsService {

    private final MerchantApplicationMapper merchantApplicationMapper;
    private final MerchantMapper merchantMapper;

    public MerchantApplicationResponse applyMerchant(Long userId, MerchantApplicationRequest request) {
        if (merchantMapper.selectCount(new QueryWrapper<Merchant>().eq("owner_user_id", userId)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前用户已是商家，无需重复申请");
        }
        if (merchantApplicationMapper.selectCount(new QueryWrapper<MerchantApplication>()
                .eq("user_id", userId)
                .eq("status", "PENDING")) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有待审核的入驻申请");
        }
        LocalDateTime now = LocalDateTime.now();
        MerchantApplication merchantApplication = new MerchantApplication();
        merchantApplication.setUserId(userId);
        merchantApplication.setShopName(request.shopName());
        merchantApplication.setContactName(request.contactName());
        merchantApplication.setContactMobile(request.contactMobile());
        merchantApplication.setLicenseNo(request.licenseNo());
        merchantApplication.setStatus("PENDING");
        merchantApplication.setCreatedAt(now);
        merchantApplication.setUpdatedAt(now);
        merchantApplicationMapper.insert(merchantApplication);
        return toApplicationResponse(merchantApplication);
    }

    public MerchantApplicationResponse getCurrentMerchantApplication(Long userId) {
        MerchantApplication application = merchantApplicationMapper.selectOne(
                new QueryWrapper<MerchantApplication>()
                        .eq("user_id", userId)
                        .orderByDesc("id")
                        .last("LIMIT 1"));
        return application == null ? null : toApplicationResponse(application);
    }

    private static MerchantApplicationResponse toApplicationResponse(MerchantApplication application) {
        return new MerchantApplicationResponse(
                String.valueOf(application.getId()),
                String.valueOf(application.getUserId()),
                application.getShopName(),
                application.getContactName(),
                application.getContactMobile(),
                maskLicenseNo(application.getLicenseNo()),
                application.getStatus(),
                application.getRejectReason(),
                application.getCreatedAt()
        );
    }

    /** 证件号属敏感信息，回显只保留首尾少量字符，不能输出完整值。 */
    private static String maskLicenseNo(String licenseNo) {
        if (licenseNo == null || licenseNo.isEmpty()) {
            return licenseNo;
        }
        if (licenseNo.length() <= 8) {
            return "****";
        }
        return licenseNo.substring(0, 3) + "****" + licenseNo.substring(licenseNo.length() - 2);
    }
}
