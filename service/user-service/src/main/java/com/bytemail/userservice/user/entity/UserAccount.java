package com.bytemail.userservice.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_account")
/**
 * 用户账号实体类。
 * 一个 UserAccount 对象对应 user_account 表中的一条记录。
 */
public class UserAccount {
    // 用户唯一编号，由数据库自增生成；新增用户时不用手动赋值
    @TableId(type = IdType.AUTO)
    private Long id;

    // 手机号，当前用于注册和登录；数据库唯一索引防止重复
    private String mobile;

    // 邮箱，可为空；填写时不能与其他用户重复
    private String email;

    // 密码哈希，对应 password_hash 字段
    // 不保存明文密码，也不应返回前端或输出到日志
    private String passwordHash;

    // 用户昵称，用于页面展示；不同用户可以使用相同昵称
    private String nickname;

    // 头像图片地址，对应 avatar_url 字段；未设置时可以为空
    private String avatarUrl;

    // 账号状态：ACTIVE 表示正常，DISABLED 表示禁用
    private String status;

    // 令牌版本号，对应 token_version 字段，数据库默认值为 0
    // 后续接入 JWT 时，通过比较令牌版本和数据库版本判断令牌是否有效
    // 仅修改这个字段不会自动使令牌失效，还需要认证代码执行校验
    private Long tokenVersion;

    // 创建时间，对应 created_at 字段
    // LocalDateTime 本身不包含时区，读写时按项目约定使用 UTC
    private LocalDateTime createdAt;

    // 最后修改时间，对应 updated_at 字段
    // 按当前建表规则，由数据库在记录发生变更时自动更新
    private LocalDateTime updatedAt;
}
