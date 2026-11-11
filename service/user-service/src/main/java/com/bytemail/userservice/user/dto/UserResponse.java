package com.bytemail.userservice.user.dto;

public record UserResponse (
        String UserId,
        String Nickname,
        String avatarUrl) {
}
