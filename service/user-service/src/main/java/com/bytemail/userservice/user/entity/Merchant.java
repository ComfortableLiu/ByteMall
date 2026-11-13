package com.bytemail.userservice.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 正式商家店铺，对应 merchant 表，保存当前运营资料。
 */
@Data
@TableName("merchant")
public class Merchant {

    /** 商家ID，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺所属用户ID，一个用户只能拥有一个店铺。 */
    private Long ownerUserId;

    /** 当前店铺名称。 */
    private String shopName;

    /** 店铺Logo URL，可为空。 */
    private String logoUrl;

    /** 店铺联系人姓名。 */
    private String contactName;

    /** 店铺联系电话。 */
    private String contactMobile;

    /** 商家状态：PENDING、ACTIVE或SUSPENDED。 */
    private String status;

    /** 创建时间，按UTC存储。 */
    private LocalDateTime createdAt;

    /** 最后更新时间，按UTC存储。 */
    private LocalDateTime updatedAt;
}
