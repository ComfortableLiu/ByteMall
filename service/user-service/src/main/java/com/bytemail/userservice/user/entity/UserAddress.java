package com.bytemail.userservice.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收货地址，对应 user_address 表。
 */
@Data
@TableName("user_address")
public class UserAddress {

    /** 地址ID，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 地址所属用户ID。 */
    private Long userId;

    /** 收货人姓名。 */
    private String receiverName;

    /** 收货人手机号。 */
    private String receiverMobile;

    /** 省份。 */
    private String province;

    /** 城市。 */
    private String city;

    /** 区/县。 */
    private String district;

    /** 详细地址。 */
    private String detailAddress;

    /** 邮政编码，可为空。 */
    private String postalCode;

    /** 是否默认地址：true为默认地址，false为普通地址。 */
    private Boolean isDefault;

    /** 创建时间，按UTC存储。 */
    private LocalDateTime createdAt;

    /** 最后更新时间，按UTC存储。 */
    private LocalDateTime updatedAt;
}
