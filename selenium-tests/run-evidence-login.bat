@echo off
setlocal

set BASE_URL=%UI_BASE_URL%
if "%BASE_URL%"=="" set BASE_URL=http://localhost:3000

set USERNAME=%UI_USERNAME%
if "%USERNAME%"=="" set USERNAME=user_a

set PASSWORD=%UI_PASSWORD%
if "%PASSWORD%"=="" set PASSWORD=Admin@123

echo Running RemainingEvidenceRunner (TC01 - TC08)...
echo BASE_URL=%BASE_URL%
echo USERNAME=%USERNAME%

call "..\auth-service-social-media\mvnw.cmd" -f "%~dp0pom.xml" -Dexec.mainClass=com.socialmedia.selenium.manual.RemainingEvidenceRunner -Dui.baseUrl=%BASE_URL% -Dui.username=%USERNAME% -Dui.password=%PASSWORD% -Dui.headless=false exec:java

endlocal
