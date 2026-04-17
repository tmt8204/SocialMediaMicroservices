package com.socialmedia.social_media_api_gateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.auth")
public class GatewayAuthProperties {

    private List<String> publicPaths = new ArrayList<>(List.of("/api/auth/**"));
    private List<String> adminRoles = new ArrayList<>(List.of("ADMIN", "MODERATOR"));
    private RevocationCheck revocationCheck = new RevocationCheck();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public List<String> getAdminRoles() {
        return adminRoles;
    }

    public void setAdminRoles(List<String> adminRoles) {
        this.adminRoles = adminRoles;
    }


    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public RevocationCheck getRevocationCheck() {
        return revocationCheck;
    }

    public void setRevocationCheck(RevocationCheck revocationCheck) {
        this.revocationCheck = revocationCheck;
    }

    public static class RevocationCheck {
        private boolean enabled = true;
        private String mode = "mongo";
        private String tokenType = "ACCESS";
        private AuthService authService = new AuthService();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public AuthService getAuthService() {
            return authService;
        }

        public void setAuthService(AuthService authService) {
            this.authService = authService;
        }
    }

    public static class AuthService {
        private String introspectionUrl;
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 2000;

        public String getIntrospectionUrl() {
            return introspectionUrl;
        }

        public void setIntrospectionUrl(String introspectionUrl) {
            this.introspectionUrl = introspectionUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }
}
