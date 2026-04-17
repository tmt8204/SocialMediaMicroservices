@echo off
echo ======================================
echo  Starting ALL Social Media Services
echo ======================================

echo [1/8] Starting Auth Service (8081)...
start "Auth-Service-8081" cmd /k "cd /d d:\Do_an_J2ee\Chieu5_Nhom11\Chieu5_Nhom11\SocialMediaMicroservices\auth-service-social-media && mvnw.cmd spring-boot:run"

echo [2/8] Starting User Service (8082)...
start "User-Service-8082" cmd /k "cd /d d:\Do_an_J2ee\Chieu5_Nhom11\Chieu5_Nhom11\SocialMediaMicroservices\social-media-user-service && mvnw.cmd spring-boot:run"

echo [3/8] Starting Social Service (8083)...
start "Social-Service-8083" cmd /k "cd /d d:\Do_an_J2ee\Chieu5_Nhom11\Chieu5_Nhom11\SocialMediaMicroservices\social-media-social-service && mvnw.cmd spring-boot:run"

echo [4/8] Starting Media Service (8084)...
start "Media-Service-8084" cmd /k "cd /d d:\Do_an_J2ee\Chieu5_Nhom11\Chieu5_Nhom11\SocialMediaMicroservices\social-media-media-service && mvnw.cmd spring-boot:run"

echo [5/8] Starting Chat Service (8085)...
start "Chat-Service-8085" cmd /k "cd /d d:\Do_an_J2ee\Chieu5_Nhom11\Chieu5_Nhom11\SocialMediaMicroservices\social-media-chat-service && mvnw.cmd spring-boot:run"

echo [6/8] Starting Notification Service (8086)...
start "Notification-Service-8086" cmd /k "cd /d d:\Do_an_J2ee\Chieu5_Nhom11\Chieu5_Nhom11\SocialMediaMicroservices\social-media-notification-service && mvnw.cmd spring-boot:run"

echo [7/8] Starting API Gateway (8080)...
start "API-Gateway-8080" cmd /k "cd /d d:\Do_an_J2ee\Chieu5_Nhom11\Chieu5_Nhom11\SocialMediaMicroservices\social-media-api-gateway && mvnw.cmd spring-boot:run"

echo [8/8] Starting Admin Service (8087)...
start "Admin-Service-8087" cmd /k "cd /d d:\Do_an_J2ee\Chieu5_Nhom11\Chieu5_Nhom11\SocialMediaMicroservices\social-media-admin-service && mvnw.cmd spring-boot:run"

echo ======================================
echo  All 8 services are starting!
echo  Wait ~30s for all to be ready
echo ======================================
pause
