@echo off
echo ==============================================
echo   STARTING 8 MICROSERVICES WITH JAVA 21
echo ==============================================
echo.

set JAVA_CMD="C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot\bin\java.exe"
set DB_URL="--spring.datasource.url=jdbc:sqlserver://localhost;databaseName=SocialMediaDB;encrypt=true;trustServerCertificate=true"

:: Fix loi CORS chung cho tat ca API
set APP_CORS_ALLOWED_ORIGIN=http://localhost,https://gummynetwork.work,https://www.gummynetwork.work

:: Fix rieng loi CORS cua WebSocket Chat (De khong bi do man hinh 'Ket noi chat chua san sang')
set CHAT_WEBSOCKET_ALLOWED_ORIGINS=http://localhost,https://gummynetwork.work,https://www.gummynetwork.work

start "Auth-Service" /b %JAVA_CMD% -jar auth-service-social-media\target\social-media-auth-service-0.0.1-SNAPSHOT.jar
start "User-Service" /b %JAVA_CMD% -jar social-media-user-service\target\user-service-social-media-0.0.1-SNAPSHOT.jar %DB_URL%
start "Social-Service" /b %JAVA_CMD% -jar social-media-social-service\target\social-media-social-service-0.0.1-SNAPSHOT.jar %DB_URL%
start "Media-Service" /b %JAVA_CMD% -jar social-media-media-service\target\social-media-media-service-0.0.1-SNAPSHOT.jar %DB_URL%
start "Chat-Service" /b %JAVA_CMD% -jar social-media-chat-service\target\social-media-chat-service-0.0.1-SNAPSHOT.jar %DB_URL%
start "Notification-Service" /b %JAVA_CMD% -jar social-media-notification-service\target\social-media-notification-service-0.0.1-SNAPSHOT.jar %DB_URL%
start "Admin-Service" /b %JAVA_CMD% -jar social-media-admin-service\target\social-media-admin-service-0.0.1-SNAPSHOT.jar %DB_URL%

echo Waiting 15 seconds for core services to initialize before starting API Gateway...
timeout /t 15

start "API-Gateway" /b %JAVA_CMD% -jar social-media-api-gateway\target\social-media-api-gateway-0.0.1-SNAPSHOT.jar

echo.
echo ==============================================
echo  ALL MICROSERVICES HAVE BEEN STARTED!
echo ==============================================
pause
