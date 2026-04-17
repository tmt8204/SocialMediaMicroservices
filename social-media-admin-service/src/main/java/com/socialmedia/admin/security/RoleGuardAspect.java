package com.socialmedia.admin.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.socialmedia.admin.exceptions.ForbiddenException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * AOP aspect that intercepts methods annotated with @RequireRole
 * and checks the X-Role header from the API Gateway.
 */
@Aspect
@Component
public class RoleGuardAspect {

    @Around("@annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new ForbiddenException("No request context");
        }

        HttpServletRequest request = attrs.getRequest();
        String currentRole = request.getHeader("X-Role");

        if (currentRole == null || currentRole.isBlank()) {
            throw new ForbiddenException("Missing role information");
        }

        Set<String> allowedRoles = Arrays.stream(requireRole.value())
                .collect(Collectors.toSet());

        if (!allowedRoles.contains(currentRole)) {
            throw new ForbiddenException("Insufficient permissions. Required: " + allowedRoles + ", got: " + currentRole);
        }

        return joinPoint.proceed();
    }
}
