package com.socialmedia.selenium.manual;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

public class RemainingEvidenceRunner {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String ONE_PIXEL_PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO2c7s8AAAAASUVORK5CYII=";

    private static String createdPostToken;
    private static String createdCommentToken;

    public static void main(String[] args) {
        Config config = Config.load();

        logInfo("Khoi dong RemainingEvidenceRunner");
        logInfo("Base URL: " + config.baseUrl);

        if (!isFrontendReachable(config.baseUrl)) {
            System.out.println("Khong the ket noi FE: " + config.baseUrl + " (/login hoac /)");
            System.out.println("Hay khoi dong frontend truoc khi chay Selenium.");
            return;
        }

        WebDriverManager.chromedriver().setup();

        List<TestCaseResult> results = new ArrayList<>();

        WebDriver primaryDriver = createDriver(config.headless);
        WebDriverWait primaryWait = new WebDriverWait(primaryDriver, Duration.ofSeconds(15));

        try {
            results.add(runCase("TC01_login_success", () -> tc01LoginSuccess(primaryDriver, primaryWait, config), primaryDriver));
            results.add(runCase("TC02_login_fail_wrong_password", () -> tc02LoginFail(primaryDriver, primaryWait, config), primaryDriver));
            results.add(runCase("TC03_register_success", () -> tc03Register(primaryDriver, primaryWait, config), primaryDriver));
            results.add(runCase("TC04_forgot_password_request", () -> tc04ForgotPassword(primaryDriver, primaryWait, config), primaryDriver));
            results.add(runCase("TC05_create_post_with_media", () -> tc05CreatePostWithMedia(primaryDriver, primaryWait, config), primaryDriver));
            results.add(runCase("TC06_react_and_comment", () -> tc06ReactAndComment(primaryDriver, primaryWait, config), primaryDriver));
        } finally {
            primaryDriver.quit();
        }

        WebDriver driverA = createDriver(config.headless);
        WebDriver driverB = createDriver(config.headless);
        WebDriverWait waitA = new WebDriverWait(driverA, Duration.ofSeconds(15));
        WebDriverWait waitB = new WebDriverWait(driverB, Duration.ofSeconds(15));

        try {
            results.add(runCase("TC07_chat_1_1_realtime", () -> tc07ChatRealtime(driverA, waitA, driverB, waitB, config), driverA));
        } finally {
            driverA.quit();
            driverB.quit();
        }

        printSummary(results);
    }

    private static boolean tc01LoginSuccess(WebDriver driver, WebDriverWait wait, Config config) {
        logStep("TC01", "Reset browser state va vao trang login");
        resetBrowserState(driver, config.baseUrl);

        logStep("TC01", "Nhap username/password hop le");
        fillLoginForm(driver, config.primaryUsername, config.primaryPassword);

        logStep("TC01", "Bam nut Dang Nhap");
        click(driver,
            By.cssSelector("[data-testid='login-submit']"),
            By.xpath("//button[@type='submit']")
        );

        boolean pass = waitForCondition(wait, d -> d.getCurrentUrl().contains("/feed"));
        logStep("TC01", "Ket qua URL hien tai: " + driver.getCurrentUrl());
        return pass;
    }

    private static boolean tc02LoginFail(WebDriver driver, WebDriverWait wait, Config config) {
        logStep("TC02", "Reset browser state va vao trang login");
        resetBrowserState(driver, config.baseUrl);

        logStep("TC02", "Nhap mat khau sai");
        fillLoginForm(driver, config.primaryUsername, config.primaryPassword + "_invalid");

        logStep("TC02", "Bam nut Dang Nhap");
        click(driver,
            By.cssSelector("[data-testid='login-submit']"),
            By.xpath("//button[@type='submit']")
        );

        boolean pass = waitForCondition(wait, d -> {
            String page = d.getPageSource().toLowerCase(Locale.ROOT);
            return page.contains("invalid username or password");
        });
        logStep("TC02", "Ket qua URL hien tai: " + driver.getCurrentUrl() + " | toast 'Invalid username or password' visible: " + pass);
        return pass;
    }

    private static boolean tc03Register(WebDriver driver, WebDriverWait wait, Config config) {
        logStep("TC03", "Reset browser state va vao trang register");
        resetBrowserState(driver, config.baseUrl);
        driver.get(config.baseUrl + "/register");

        String suffix = String.valueOf(System.currentTimeMillis());
        String username = "auto_tc03_" + suffix;
        String email = "auto_tc03_" + suffix + "@gmail.com";

        logStep("TC03", "Nhap thong tin dang ky: " + username + " / " + email);

        type(driver, By.xpath("//input[@placeholder='Họ và tên']"), "Selenium TC03 " + suffix);
        type(driver, By.xpath("//input[@placeholder='Tên đăng nhập']"), username);
        type(driver, By.xpath("//input[@placeholder='Email']"), email);
        type(driver, By.xpath("//input[@placeholder='Mật khẩu']"), config.registerPassword);

        click(driver,
            By.xpath("//button[@type='submit' and contains(.,'Tạo Tài Khoản')]"),
            By.xpath("//button[@type='submit']")
        );

        logStep("TC03", "Cho dieu huong sang /feed");
        return waitForCondition(wait, d -> d.getCurrentUrl().contains("/feed"));
    }

    private static boolean tc04ForgotPassword(WebDriver driver, WebDriverWait wait, Config config) {
        logStep("TC04", "Reset browser state va vao trang login");
        resetBrowserState(driver, config.baseUrl);
        driver.get(config.baseUrl + "/login");

        logStep("TC04", "Mo popup Quen mat khau");
        click(driver,
            By.cssSelector("a.forgot"),
            By.xpath("//a[contains(.,'Quên mật khẩu') or contains(.,'Quên Mật Khẩu') ]"),
            By.xpath("//*[contains(text(),'Quên mật khẩu')]")
        );

        type(driver, config.primaryEmail,
            By.xpath("//div[contains(@class,'forgot-popup')]//input[@type='email']"),
            By.xpath("//input[@placeholder='Nhập email của bạn']")
        );
        logStep("TC04", "Nhap email reset: " + config.primaryEmail);

        click(driver,
            By.xpath("//div[contains(@class,'forgot-popup')]//button[@type='submit']"),
            By.xpath("//button[contains(.,'Gửi Link')]")
        );

        logStep("TC04", "Cho toast ket qua gui link hien thi");
        return waitForCondition(wait, d -> {
            String page = d.getPageSource();
            return page.contains("Nếu email tồn tại, hệ thống đã gửi link đặt lại mật khẩu")
                || page.contains("Neu email ton tai, he thong da gui link dat lai mat khau");
        });
    }

    private static boolean tc05CreatePostWithMedia(WebDriver driver, WebDriverWait wait, Config config) throws IOException {
        logStep("TC05", "Dang nhap tai khoan chinh");
        loginWith(driver, wait, config.baseUrl, config.primaryUsername, config.primaryPassword);
        driver.get(config.baseUrl + "/feed");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("postInput")));

        createdPostToken = "TC05_AUTO_POST_" + System.currentTimeMillis();
        logStep("TC05", "Tao bai viet token: " + createdPostToken);
        type(driver, By.id("postInput"), createdPostToken);

        Path imagePath = createTempEvidenceImage();
        logStep("TC05", "Upload media: " + imagePath.toAbsolutePath());
        findFirst(driver,
            By.id("postImageInput"),
            By.xpath("//input[@type='file' and @id='postImageInput']")
        ).sendKeys(imagePath.toAbsolutePath().toString());

        click(driver,
            By.cssSelector("button.cp-post-btn"),
            By.xpath("//button[contains(@class,'cp-post-btn')]"),
            By.xpath("//button[contains(.,'Đăng')]")
        );

        logStep("TC05", "Cho bai viet moi hien thi tren feed");
        By postContent = By.xpath("//*[@id='postList']//*[contains(.,'" + createdPostToken + "')]");
        return waitForCondition(wait, d -> !d.findElements(postContent).isEmpty());
    }

    private static boolean tc06ReactAndComment(WebDriver driver, WebDriverWait wait, Config config) {
        if (createdPostToken == null || createdPostToken.isBlank()) {
            logStep("TC06", "Khong tim thay token bai viet tu TC05");
            return false;
        }

        logStep("TC06", "Mo feed va tim post token: " + createdPostToken);
        driver.get(config.baseUrl + "/feed");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("postList")));

        String postCardScope = "//div[contains(@class,'post-card')][.//*[contains(.,'" + createdPostToken + "')]]";
        By likeButtonLocator = By.xpath(postCardScope + "//div[contains(@class,'reaction-wrapper')]//button[contains(@class,'post-btn')][1]");
        logStep("TC06", "React Like");
        click(driver, likeButtonLocator);

        By commentToggleLocator = By.xpath(postCardScope + "//button[contains(.,'Bình Luận') or contains(.,'Bình luận')]");
        logStep("TC06", "Mo khu vuc comment");
        click(driver, commentToggleLocator);

        createdCommentToken = "TC06_AUTO_COMMENT_" + System.currentTimeMillis();
        logStep("TC06", "Gui comment token: " + createdCommentToken);
        By commentInputLocator = By.xpath(postCardScope + "//input[@placeholder='Viết bình luận...']");
        wait.until(ExpectedConditions.visibilityOfElementLocated(commentInputLocator));
        type(driver, commentInputLocator, createdCommentToken);
        pressKey(driver, Keys.ENTER, commentInputLocator);

        By commentAssertLocator = By.xpath(postCardScope + "//*[contains(@class,'comment-text') and contains(.,'" + createdCommentToken + "')]");
        return waitForCondition(wait, d -> !d.findElements(commentAssertLocator)
            .isEmpty());
    }

    private static boolean tc07ChatRealtime(
        WebDriver driverA,
        WebDriverWait waitA,
        WebDriver driverB,
        WebDriverWait waitB,
        Config config
    ) {
        logStep("TC07", "Dang nhap 2 tai khoan cho 2 browser");
        loginWith(driverA, waitA, config.baseUrl, config.primaryUsername, config.primaryPassword);
        loginWith(driverB, waitB, config.baseUrl, config.secondaryUsername, config.secondaryPassword);

        driverA.get(config.baseUrl + "/chat");
        driverB.get(config.baseUrl + "/chat");

        if (!waitForCondition(waitA, d -> !d.findElements(By.cssSelector(".chat-contact")).isEmpty())) {
            return false;
        }
        if (!waitForCondition(waitB, d -> !d.findElements(By.cssSelector(".chat-contact")).isEmpty())) {
            return false;
        }

        waitForCondition(waitA, d -> d.getPageSource().contains("Đang kết nối realtime"));
        waitForCondition(waitB, d -> d.getPageSource().contains("Đang kết nối realtime"));

        boolean selectedA = selectContact(driverA, config.secondaryContactName, config.secondaryUsername);
        boolean selectedB = selectContact(driverB, config.primaryContactName, config.primaryUsername);
        if (!selectedA || !selectedB) {
            logStep("TC07", "Khong chon duoc contact chat");
            return false;
        }

        String message = "TC07_CHAT_" + System.currentTimeMillis();
        logStep("TC07", "Gui tin nhan: " + message);

        By chatInputLocator = By.cssSelector("textarea.chat-textarea");
        waitA.until(ExpectedConditions.visibilityOfElementLocated(chatInputLocator));
        type(driverA, chatInputLocator, message);
        pressKey(driverA, Keys.ENTER, chatInputLocator);

        By messageLocator = By.xpath(
            "//div[contains(@class,'chat-msg-text') and contains(.,'" + message + "')]" +
                "|//*[contains(@class,'message') and contains(.,'" + message + "')]"
        );
        By previewLocator = By.xpath("//div[contains(@class,'chat-msg-time') and contains(.,'" + message + "')]");
        WebDriverWait realtimeWait = new WebDriverWait(driverB, Duration.ofSeconds(25));
        return waitForCondition(realtimeWait, d -> !d.findElements(messageLocator).isEmpty() || !d.findElements(previewLocator).isEmpty());
    }

    private static boolean tc08NonAdminCannotOpenAdmin(WebDriver driver, WebDriverWait wait, Config config) {
        logStep("TC08", "Dang nhap user thuong va truy cap /admin");
        loginWith(driver, wait, config.baseUrl, config.primaryUsername, config.primaryPassword);

        driver.get(config.baseUrl + "/admin");
        return waitForCondition(wait, d -> d.getCurrentUrl().contains("/feed"));
    }

    private static TestCaseResult runCase(String id, CaseExecutor executor, WebDriver driver) {
        logInfo("--- START " + id + " ---");
        try {
            boolean pass = executor.execute();
            saveScreenshot(driver, id + (pass ? "_PASS" : "_FAIL"));
            if (pass) {
                logInfo("--- END " + id + " => PASS ---");
                return new TestCaseResult(id, true, "");
            }

            String taggedFailure = tagFailure(id, "Dieu kien assert khong dat");
            logInfo("--- END " + id + " => FAIL: " + taggedFailure + " ---");
            return new TestCaseResult(id, false, taggedFailure);
        } catch (Exception ex) {
            saveScreenshot(driver, id + "_ERROR");
            String compactMessage = compactError(ex);
            String taggedError = tagFailure(id, compactMessage);
            logInfo("--- END " + id + " => ERROR: " + taggedError + " ---");
            return new TestCaseResult(id, false, taggedError);
        }
    }

    private static void loginWith(WebDriver driver, WebDriverWait wait, String baseUrl, String usernameValue, String passwordValue) {
        logStep("LOGIN", "Reset browser state va mo login page");
        resetBrowserState(driver, baseUrl);

        type(driver, usernameValue,
            By.cssSelector("[data-testid='login-username']"),
            By.xpath("//input[@placeholder='Tên đăng nhập']")
        );
        logStep("LOGIN", "Nhap username: " + usernameValue);

        type(driver, passwordValue,
            By.cssSelector("[data-testid='login-password']"),
            By.xpath("//input[@placeholder='Mật khẩu']")
        );
        logStep("LOGIN", "Nhap password va submit");

        click(driver,
            By.cssSelector("[data-testid='login-submit']"),
            By.xpath("//button[@type='submit']")
        );

        wait.until(ExpectedConditions.urlContains("/feed"));
        logStep("LOGIN", "Dang nhap xong, URL: " + driver.getCurrentUrl());
    }

    private static void fillLoginForm(WebDriver driver, String usernameValue, String passwordValue) {
        type(driver, usernameValue,
            By.cssSelector("[data-testid='login-username']"),
            By.xpath("//input[@placeholder='Tên đăng nhập']")
        );

        type(driver, passwordValue,
            By.cssSelector("[data-testid='login-password']"),
            By.xpath("//input[@placeholder='Mật khẩu']")
        );
    }

    private static void resetBrowserState(WebDriver driver, String baseUrl) {
        driver.manage().deleteAllCookies();
        driver.get(baseUrl + "/");

        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.localStorage.clear();");
        js.executeScript("window.sessionStorage.clear();");

        driver.get(baseUrl + "/login");
        driver.navigate().refresh();
    }

    private static void click(WebDriver driver, By... selectors) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                WebElement element = findFirst(driver, selectors);
                scrollIntoView(driver, element);
                try {
                    element.click();
                } catch (Exception clickEx) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
                }
                return;
            } catch (StaleElementReferenceException ignore) {
                // Retry with fresh lookup.
            }
        }
        throw new StaleElementReferenceException("Khong the click do stale element voi cac selector da cung cap");
    }

    private static WebDriver createDriver(boolean headless) {
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1440,900");
        return new ChromeDriver(options);
    }

    private static WebElement findPostCardByToken(WebDriver driver, String token) {
        String xpath = "//div[contains(@class,'post-card')][.//*[contains(.,'" + token + "')]]";
        return findFirst(driver, By.xpath(xpath));
    }

    private static boolean selectContact(WebDriver driver, String preferredName, String fallbackName) {
        String target = preferredName;
        if (target == null || target.isBlank()) {
            target = fallbackName;
        }

        String normalizedTarget = target == null ? "" : target.toLowerCase(Locale.ROOT);

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                List<WebElement> contacts = driver.findElements(By.cssSelector(".chat-contact"));
                if (contacts.isEmpty()) {
                    continue;
                }

                if (!normalizedTarget.isBlank()) {
                    for (WebElement contact : contacts) {
                        String name = safeText(contact, By.cssSelector(".chat-contact-name")).toLowerCase(Locale.ROOT);
                        if (name.contains(normalizedTarget)) {
                            scrollIntoView(driver, contact);
                            try {
                                contact.click();
                            } catch (Exception clickEx) {
                                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", contact);
                            }
                            return true;
                        }
                    }
                }

                WebElement first = contacts.get(0);
                scrollIntoView(driver, first);
                try {
                    first.click();
                } catch (Exception clickEx) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", first);
                }
                return true;
            } catch (StaleElementReferenceException ignore) {
                // Retry with a fresh contact list.
            }
        }

        return false;
    }

    private static String safeText(WebElement parent, By locator) {
        try {
            return parent.findElement(locator).getText();
        } catch (NoSuchElementException ignore) {
            return "";
        }
    }

    private static void type(WebDriver driver, By locator, String value) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                WebElement element = findFirst(driver, locator);
                scrollIntoView(driver, element);
                element.clear();
                element.sendKeys(value);
                return;
            } catch (StaleElementReferenceException ignore) {
                // Retry because login/register pages can rerender the input immediately after focus.
            }
        }
        throw new StaleElementReferenceException("Khong the nhap lieu do stale element: " + locator);
    }

    private static void type(WebDriver driver, String value, By... selectors) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                WebElement element = findFirst(driver, selectors);
                scrollIntoView(driver, element);
                element.clear();
                element.sendKeys(value);
                return;
            } catch (StaleElementReferenceException ignore) {
                // Retry with fresh lookup on each attempt.
            }
        }
        throw new StaleElementReferenceException("Khong the nhap lieu do stale element voi cac selector da cung cap");
    }

    private static void type(WebDriver driver, WebElement element, String value) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                scrollIntoView(driver, element);
                element.clear();
                element.sendKeys(value);
                return;
            } catch (StaleElementReferenceException ignore) {
                // Caller should prefer type(driver, By, value) for stale-safe re-find behavior.
            }
        }
        throw new StaleElementReferenceException("Khong the nhap lieu do stale element");
    }

    private static void scrollIntoView(WebDriver driver, WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
    }

    private static void pressKey(WebDriver driver, Keys key, By... selectors) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                WebElement element = findFirst(driver, selectors);
                scrollIntoView(driver, element);
                element.sendKeys(key);
                return;
            } catch (StaleElementReferenceException ignore) {
                // Retry with fresh lookup.
            }
        }
        throw new StaleElementReferenceException("Khong the gui phim do stale element voi cac selector da cung cap");
    }

    private static WebElement findFirst(WebDriver driver, By... selectors) {
        try {
            WebDriverWait lookupWait = new WebDriverWait(driver, Duration.ofSeconds(8));
            return lookupWait.until(d -> {
                for (By selector : selectors) {
                    try {
                        List<WebElement> found = d.findElements(selector);
                        if (!found.isEmpty()) {
                            return found.get(0);
                        }
                    } catch (StaleElementReferenceException ignore) {
                        // Retry in next polling cycle.
                    }
                }
                return null;
            });
        } catch (TimeoutException ex) {
            throw new NoSuchElementException("Khong tim thay phan tu voi cac selector da cung cap");
        }
    }

    private static boolean waitForCondition(WebDriverWait wait, ExpectedCondition<Boolean> condition) {
        try {
            return wait.until(condition);
        } catch (TimeoutException ignore) {
            return false;
        }
    }

    private static Path createTempEvidenceImage() throws IOException {
        Path assetDir = Paths.get("evidence", "assets");
        Files.createDirectories(assetDir);
        Path image = assetDir.resolve("tc05_upload.png");
        Files.write(image, Base64.getDecoder().decode(ONE_PIXEL_PNG_BASE64));
        return image;
    }

    private static void saveScreenshot(WebDriver driver, String label) {
        try {
            byte[] imageBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Path evidenceDir = Paths.get("evidence");
            Files.createDirectories(evidenceDir);

            String fileName = LocalDateTime.now().format(TS_FORMAT) + "_" + label + ".png";
            Files.write(evidenceDir.resolve(fileName), imageBytes);
        } catch (IOException e) {
            System.out.println("Khong the luu screenshot: " + e.getMessage());
        }
    }

    private static boolean isUrlReachable(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            // Treat any HTTP response as reachable, including 5xx.
            return status >= 100 && status < 600;
        } catch (IOException ignore) {
            return isTcpPortReachable(url);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isTcpPortReachable(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }

            int port = uri.getPort();
            if (port <= 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1500);
                return true;
            }
        } catch (IOException | URISyntaxException ignore) {
            return false;
        }
    }

    private static boolean isFrontendReachable(String baseUrl) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(baseUrl + "/login");
        candidates.add(baseUrl + "/");

        if (baseUrl.contains("localhost")) {
            String ipv4BaseUrl = baseUrl.replace("localhost", "127.0.0.1");
            candidates.add(ipv4BaseUrl + "/login");
            candidates.add(ipv4BaseUrl + "/");

            String ipv6BaseUrl = baseUrl.replace("localhost", "[::1]");
            candidates.add(ipv6BaseUrl + "/login");
            candidates.add(ipv6BaseUrl + "/");
        }

        for (int attempt = 1; attempt <= 4; attempt++) {
            for (String candidate : candidates) {
                if (isUrlReachable(candidate)) {
                    logInfo("Preflight OK: " + candidate + " (attempt " + attempt + ")");
                    return true;
                }
                logInfo("Preflight FAIL: " + candidate + " (attempt " + attempt + ")");
            }

            if (attempt < 4) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false;
    }

    private static void printSummary(List<TestCaseResult> results) {
        long passCount = results.stream().filter(TestCaseResult::pass).count();

        System.out.println("========================================");
        System.out.println("KET QUA CHAY REMAINING TEST CASES");
        for (TestCaseResult result : results) {
            String extra = result.message().isBlank() ? "" : (" - " + result.message());
            System.out.println(result.id() + ": " + (result.pass() ? "PASS" : "FAIL") + extra);
        }
        System.out.println("TONG KET: " + passCount + "/" + results.size() + " PASS");
        System.out.println("Screenshot luu tai thu muc: selenium-tests/evidence");
        System.out.println("========================================");
    }

    private static String compactError(Exception ex) {
        String message = ex == null ? "Unknown error" : ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex == null ? "Unknown error" : ex.getClass().getSimpleName();
        }

        String singleLine = message.split("\\R", 2)[0].trim();
        if (singleLine.length() > 220) {
            return singleLine.substring(0, 220) + "...";
        }
        return singleLine;
    }

    private static String tagFailure(String testCaseId, String message) {
        String tag = classifyFailureTag(testCaseId, message);
        return "[" + tag + "] " + message;
    }

    private static String classifyFailureTag(String testCaseId, String message) {
        String normalized = (message == null ? "" : message).toLowerCase(Locale.ROOT);

        if (normalized.contains("stale")) {
            return "STALE";
        }
        if (normalized.contains("khong tim thay phan tu") || normalized.contains("no such element") || normalized.contains("timeout")) {
            return "ASSERT";
        }
        if (normalized.contains("preflight") || normalized.contains("connect") || normalized.contains("unable to connect") || normalized.contains("ket noi")) {
            return "ENV";
        }
        if (normalized.contains("chat") || normalized.contains("websocket") || normalized.contains("realtime") || testCaseId.contains("TC07")) {
            return "REALTIME";
        }
        return "ASSERT";
    }

    private static void logInfo(String message) {
        System.out.println("[INFO] " + message);
    }

    private static void logStep(String testId, String message) {
        System.out.println("[" + testId + "] " + message);
    }

    private interface CaseExecutor {
        boolean execute() throws Exception;
    }

    private record TestCaseResult(String id, boolean pass, String message) {
    }

    private record Config(
        String baseUrl,
        String primaryUsername,
        String primaryPassword,
        String primaryEmail,
        String secondaryUsername,
        String secondaryPassword,
        String primaryContactName,
        String secondaryContactName,
        String registerPassword,
        boolean headless
    ) {
        static Config load() {
            return new Config(
                getConfig("ui.baseUrl", "UI_BASE_URL", "http://localhost:3000"),
                getConfig("ui.username", "UI_USERNAME", "seleniumtest1"),
                getConfig("ui.password", "UI_PASSWORD", "Abc@1234"),
                getConfig("ui.email", "UI_EMAIL", "seleniumtest1@gmail.com"),
                getConfig("ui.username2", "UI_USERNAME_2", "seleniumtest2"),
                getConfig("ui.password2", "UI_PASSWORD_2", "Abc@1234"),
                getConfig("ui.contactName", "UI_CONTACT_NAME", "tester 01"),
                getConfig("ui.contactName2", "UI_CONTACT_NAME_2", "tester 02"),
                getConfig("ui.registerPassword", "UI_REGISTER_PASSWORD", "Abc@12345"),
                Boolean.parseBoolean(getConfig("ui.headless", "UI_HEADLESS", "false"))
            );
        }
    }

    private static String getConfig(String systemPropertyName, String envName, String defaultValue) {
        String fromSystemProperty = System.getProperty(systemPropertyName);
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return fromSystemProperty.trim();
        }

        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }

        return defaultValue;
    }
}