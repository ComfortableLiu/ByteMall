package com.bytemail.userservice.auth;

public record AuthRequest (
        String mobile,
        String password
){}