package com.bytemail.userservice.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家入驻申请，对应 merchant_application 表，保存申请资料及审核历史。
 */
@Data
@TableName("merchant_application")
public class MerchantApplication {

    /** 入驻申请ID，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提交申请的用户ID。 */
    private Long userId;

    /** 申请时填写的店铺名称。 */
    private String shopName;

    /** 联系人姓名。 */
    private String contactName;

    /** 联系电话。 */
    private String contactMobile;

    /** 资质证件编号，属于敏感信息，接口不得完整回显。 */
    private String licenseNo;

    /** 审核状态：PENDING、APPROVED或REJECTED。 */
    private String status;

    /** 驳回原因，审核未驳回时为空。 */
    private String rejectReason;

    /** 审核人ID，未审核时为空。 */
    private Long reviewedBy;

    /** 审核时间，未审核时为空，按UTC存储。 */
    private LocalDateTime reviewedAt;

    /** 申请时间，按UTC存储。 */
    private LocalDateTime createdAt;

    /** 最后更新时间，按UTC存储。 */
    private LocalDateTime updatedAt;

    // pending_user_id由数据库生成，用于限制同一用户只有一条待审核申请，不在实体中映射。
}
