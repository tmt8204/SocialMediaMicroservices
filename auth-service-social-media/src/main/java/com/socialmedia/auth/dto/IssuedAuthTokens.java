package com.socialmedia.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssuedAuthTokens {

    private AuthResponse authResponse;

    private String refreshToken;
}