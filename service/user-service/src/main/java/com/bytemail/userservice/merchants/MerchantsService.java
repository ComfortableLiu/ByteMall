package com.bytemail.userservice.merchants;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemail.userservice.user.entity.Merchant;
import com.bytemail.userservice.user.entity.MerchantApplication;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * 一键审核通过：申请置为APPROVED并同事务创建商家店铺，用户即具备商家身份。
     * 重复审核同一条已通过申请按幂等返回；演示简化为任意登录账号可审核，生产环境须限制为平台运营角色。
     */
    @Transactional
    public MerchantApplicationResponse approveApplication(Long userId, Long applicationId) {
        MerchantApplication application = requireApplication(applicationId);
        if ("APPROVED".equals(application.getStatus())) {
            return toApplicationResponse(application);
        }
        requirePending(application);

        if (merchantMapper.selectCount(new QueryWrapper<Merchant>()
                .eq("owner_user_id", application.getUserId())) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该用户已是商家，无需重复开通");
        }
        LocalDateTime now = LocalDateTime.now();
        application.setStatus("APPROVED");
        application.setReviewedBy(userId);
        application.setReviewedAt(now);
        application.setUpdatedAt(now);
        merchantApplicationMapper.updateById(application);

        Merchant merchant = new Merchant();
        merchant.setOwnerUserId(application.getUserId());
        merchant.setShopName(application.getShopName());
        merchant.setContactName(application.getContactName());
        merchant.setContactMobile(application.getContactMobile());
        merchant.setStatus("ACTIVE");
        merchant.setCreatedAt(now);
        merchant.setUpdatedAt(now);
        merchantMapper.insert(merchant);

        return toApplicationResponse(application);
    }

    /** 一键驳回：必须写明驳回原因；重复驳回同一条申请按幂等返回。 */
    @Transactional
    public MerchantApplicationResponse rejectApplication(Long userId, Long applicationId, String reason) {
        MerchantApplication application = requireApplication(applicationId);
        if ("REJECTED".equals(application.getStatus())) {
            return toApplicationResponse(application);
        }
        requirePending(application);

        LocalDateTime now = LocalDateTime.now();
        application.setStatus("REJECTED");
        application.setRejectReason(reason);
        application.setReviewedBy(userId);
        application.setReviewedAt(now);
        application.setUpdatedAt(now);
        merchantApplicationMapper.updateById(application);
        return toApplicationResponse(application);
    }

    private MerchantApplication requireApplication(Long applicationId) {
        MerchantApplication application = merchantApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "入驻申请不存在");
        }
        return application;
    }

    /** 只有PENDING可被审核；驳回单不能直接通过、通过单不能直接驳回。 */
    private static void requirePending(MerchantApplication application) {
        if (!"PENDING".equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "该申请已处理完毕，当前状态: " + application.getStatus());
        }
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
