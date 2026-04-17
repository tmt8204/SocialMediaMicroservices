# Selenium Tests (TC01-TC08)

Module nay tich hop Selenium + TestNG va evidence runner de chay bo test TC01-TC08.

## Yeu cau
- JDK 17+
- Maven 3.9+
- Chrome da cai dat
- Frontend dang chay tai http://localhost:3000

## Cac bien cau hinh
Co the truyen qua Maven system properties hoac bien moi truong:
- `ui.baseUrl` hoac `UI_BASE_URL` (mac dinh: http://localhost:3000)
- `ui.username` hoac `UI_USERNAME` (mac dinh: user_a)
- `ui.password` hoac `UI_PASSWORD` (mac dinh: Admin@123)
- `ui.headless` (mac dinh: false)

## Chay test
```bash
cd selenium-tests
mvn test -Dui.baseUrl=http://localhost:3000 -Dui.username=user_a -Dui.password=your_password

```

## Chay khi may KHONG cai Maven
Tu root project, dung Maven Wrapper co san trong backend service:

```bat
cd SocialMediaMicroservices
auth-service-social-media\mvnw.cmd -f selenium-tests\pom.xml test -Dui.baseUrl=http://localhost:3000 -Dui.username=user_a -Dui.password=your_password
```

Hoac chay script co san trong module Selenium:

```bat
cd SocialMediaMicroservices\selenium-tests
run-evidence-login.bat
```

## Chay theo dang main class de chup bao cao
Ban nay in ro TC01-TC08 PASS-FAIL va luu screenshot vao thu muc evidence.

```bat
cd SocialMediaMicroservices\selenium-tests
run-evidence-login.bat
```

## Chay headless
```bash
mvn test -Dui.baseUrl=http://localhost:3000 -Dui.username=user_a -Dui.password=your_password -Dui.headless=true
```
