package com.socialmedia.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeRoleRequest {
    @NotBlank(message = "roleName is required")
    private String roleName;
}
