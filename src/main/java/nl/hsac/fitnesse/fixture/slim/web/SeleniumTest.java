package nl.hsac.fitnesse.fixture.slim.web;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

import nl.hsac.fitnesse.fixture.slim.SlimFixture;
import nl.hsac.fitnesse.fixture.slim.SlimFixtureException;
import nl.hsac.fitnesse.fixture.util.SeleniumHelper;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;

/**
 * Fixture class for tests using Selenium.
 */
public class SeleniumTest extends SlimFixture {
    private SeleniumHelper seleniumHelper = getEnvironment().getSeleniumHelper();
    private int secondsBeforeTimeout;
    private int waitAfterScroll = 150;
    private String screenshotBase = new File(filesDir, "screenshots").getPath() + "/";
    private String screenshotHeight = "200";

    @Override
    protected Throwable handleException(Method method, Object[] arguments, Throwable t) {
        Throwable result;
        if (!(t instanceof SlimFixtureException)) {
            String msg = getSlimFixtureExceptionMessage("exception", null, t);
            result = new SlimFixtureException(false, msg, t);
        } else {
            result = super.handleException(method, arguments, t);
        }
        return result;
    }

    public SeleniumTest() {
        secondsBeforeTimeout(seleniumHelper.getDefaultTimeoutSeconds());
        ensureActiveTabIsNotClosed();
    }

    public SeleniumTest(int secondsBeforeTimeout) {
        secondsBeforeTimeout(secondsBeforeTimeout);
        ensureActiveTabIsNotClosed();
    }

    public boolean open(String address) {
        final String url = getUrl(address);
        try {
            getNavigation().to(url);
        } catch (TimeoutException e) {
            handleTimeoutException(e);
        }
        return true;
    }

    public boolean back() {
        getNavigation().back();
        return true;
    }

    public boolean forward() {
        getNavigation().forward();
        return true;
    }

    public boolean refresh() {
        getNavigation().refresh();
        return true;
    }

    private WebDriver.Navigation getNavigation() {
        return getSeleniumHelper().navigate();
    }

    public boolean switchToNextTab() {
        boolean result = false;
        List<String> tabs = getTabHandles();
        if (tabs.size() > 1) {
            int currentTab = getCurrentTabIndex(tabs);
            int nextTab = currentTab + 1;
            if (nextTab == tabs.size()) {
                nextTab = 0;
            }
            goToTab(tabs, nextTab);
            result = true;
        }
        return result;
    }

    public boolean switchToPreviousTab() {
        boolean result = false;
        List<String> tabs = getTabHandles();
        if (tabs.size() > 1) {
            int currentTab = getCurrentTabIndex(tabs);
            int nextTab = currentTab - 1;
            if (nextTab < 0) {
                nextTab = tabs.size() - 1;
            }
            goToTab(tabs, nextTab);
            result = true;
        }
        return result;
    }

    public boolean closeTab() {
        boolean result = false;
        List<String> tabs = getTabHandles();
        int currentTab = getCurrentTabIndex(tabs);
        int tabToGoTo = -1;
        if (currentTab > 0) {
            tabToGoTo = currentTab - 1;
        } else {
            if (tabs.size() > 1) {
                tabToGoTo = 1;
            }
        }
        if (tabToGoTo > -1) {
            WebDriver driver = getSeleniumHelper().driver();
            driver.close();
            goToTab(tabs, tabToGoTo);
            result = true;
        }
        return result;
    }

    public void ensureOnlyOneTab() {
        ensureActiveTabIsNotClosed();
        int tabCount = tabCount();
        for (int i = 1; i < tabCount; i++) {
            closeTab();
        }
    }

    public boolean ensureActiveTabIsNotClosed() {
        boolean result = false;
        List<String> tabHandles = getTabHandles();
        int currentTab = getCurrentTabIndex(tabHandles);
        if (currentTab < 0) {
            result = true;
            goToTab(tabHandles, 0);
        }
        return result;
    }

    public int tabCount() {
        return getTabHandles().size();
    }

    public int currentTabIndex() {
        return getCurrentTabIndex(getTabHandles()) + 1;
    }

    protected int getCurrentTabIndex(List<String> tabHandles) {
        return getSeleniumHelper().getCurrentTabIndex(tabHandles);
    }

    protected void goToTab(List<String> tabHandles, int indexToGoTo) {
        getSeleniumHelper().goToTab(tabHandles, indexToGoTo);
    }

    protected List<String> getTabHandles() {
        return getSeleniumHelper().getTabHandles();
    }

    public String alertText() {
        return waitUntilOrNull(new ExpectedCondition<String>() {
            @Override
            public String apply(WebDriver webDriver) {
                Alert alert = getAlert();
                String text = null;
                if (alert != null) {
                    text = alert.getText();
                }
                return text;
            }
        });
    }

    public boolean confirmAlert() {
        return waitUntil(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                Alert alert = getAlert();
                boolean result = false;
                if (alert != null) {
                    alert.accept();
                    result = true;
                }
                return result;
            }
        });
    }

    public boolean dismissAlert() {
        return waitUntil(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                Alert alert = getAlert();
                boolean result = false;
                if (alert != null) {
                    alert.dismiss();
                    result = true;
                }
                return result;
            }
        });
    }

    protected Alert getAlert() {
        return getSeleniumHelper().getAlert();
    }

    /**
     * Simulates pressing the 'Tab' key.
     * @return true, if an element was active the key could be sent to.
     */
    public boolean pressTab() {
        return sendKeysToActiveElement(Keys.TAB);
    }

    /**
     * Simulates pressing the 'Enter' key.
     * @return true, if an element was active the key could be sent to.
     */
    public boolean pressEnter() {
        return sendKeysToActiveElement(Keys.ENTER);
    }

    /**
     * Simulates pressing the 'Esc' key.
     * @return true, if an element was active the key could be sent to.
     */
    public boolean pressEsc() {
        return sendKeysToActiveElement(Keys.ESCAPE);
    }

    /**
     * Simulates typing a text to the current active element.
     * @param text text to type.
     * @return true, if an element was active the text could be sent to.
     */
    public boolean type(String text) {
        String value = cleanupValue(text);
        return sendKeysToActiveElement(value);
    }

    /**
     * Simulates pressing a key (or a combination of keys).
     * (Unfortunately not all combinations seem to be accepted by all drivers, e.g.
     * Chrome on OSX seems to ignore Command+A or Command+T; https://code.google.com/p/selenium/issues/detail?id=5919).
     * @param key key to press, can be a normal letter (e.g. 'M') or a special key (e.g. 'down').
     *            Combinations can be passed by separating the keys to send with '+' (e.g. Command + T).
     * @return true, if an element was active the key could be sent to.
     */
    public boolean press(String key) {
        CharSequence s;
        String[] parts = key.split("\\s*\\+\\s*");
        if (parts.length > 1
                && !"".equals(parts[0]) && !"".equals(parts[1])) {
            CharSequence[] sequence = new CharSequence[parts.length];
            for (int i = 0; i < parts.length; i++) {
                sequence[i] = parseKey(parts[i]);
            }
            s = Keys.chord(sequence);
        } else {
            s = parseKey(key);
        }

        return sendKeysToActiveElement(s);
    }

    protected CharSequence parseKey(String key) {
        CharSequence s;
        try {
            s = Keys.valueOf(key.toUpperCase());
        } catch (IllegalArgumentException e) {
            s = key;
        }
        return s;
    }

    /**
     * Simulates pressing keys.
     * @param keys keys to press.
     * @return true, if an element was active the keys could be sent to.
     */
    protected boolean sendKeysToActiveElement(CharSequence keys) {
        boolean result = false;
        WebElement element = getSeleniumHelper().getActiveElement();
        if (element != null) {
            element.sendKeys(keys);
            result = true;
        }
        return result;
    }

    protected boolean clickElement(WebElement element) {
        boolean result = false;
        if (element != null) {
            scrollIfNotOnScreen(element);
            if (isInteractable(element)) {
                element.click();
                result = true;
            }
        }
        return result;
    }

    protected boolean isInteractable(WebElement element) {
        return getSeleniumHelper().isInteractable(element);
    }

    public boolean waitForTagWithText(final String tagName, final String expectedText) {
        return waitForElementWithText(By.tagName(tagName), expectedText);
    }

    public boolean waitForClassWithText(final String cssClassName, final String expectedText) {
        return waitForElementWithText(By.className(cssClassName), expectedText);
    }

    protected boolean waitForElementWithText(final By by, String expectedText) {
        final String textToLookFor = cleanExpectedValue(expectedText);
        return waitUntilOrStop(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                boolean ok = false;

                List<WebElement> elements = webDriver.findElements(by);
                if (elements != null) {
                    for (WebElement element : elements) {
                        // we don't want stale elements to make single
                        // element false, but instead we stop processing
                        // current list and do a new findElements
                        ok = hasTextUnsafe(element, textToLookFor);
                        if (ok) {
                            // no need to continue to check other elements
                            break;
                        }
                    }
                }
                return ok;
            }
        });
    }

    protected String cleanExpectedValue(String expectedText) {
        return cleanupValue(expectedText);
    }

    protected boolean hasTextUnsafe(WebElement element, String textToLookFor) {
        boolean ok;
        String actual = getElementText(element);
        if (textToLookFor == null) {
            ok = actual == null;
        } else {
            if (actual == null) {
                actual = element.getAttribute("value");
            }
            ok = textToLookFor.equals(actual);
        }
        return ok;
    }

    public boolean waitForClass(final String cssClassName) {
        boolean result;
        result = waitUntilOrStop(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                boolean ok = false;

                WebElement element = webDriver.findElement(By.className(cssClassName));
                if (element != null) {
                    ok = true;
                }
                return ok;
            }
        });
        return result;
    }

    public boolean waitForXPathVisible(final String xPath) {
        By by = By.xpath(xPath);
        return waitForVisible(by);
    }

    protected boolean waitForVisible(final By by) {
        return waitUntilOrStop(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                Boolean result = Boolean.FALSE;
                WebElement element = getSeleniumHelper().findElement(by);
                if (element != null) {
                    scrollIfNotOnScreen(element);
                    result = element.isDisplayed();
                }
                return result;
            }
        });
    }

    public boolean clickByXPath(final String xPath) {
        return waitUntil(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                WebElement element = findByXPath(xPath);
                return clickElement(element);
            }
        });
    }

    public String textByXPath(String xPath) {
        return getTextByXPath(xPath);
    }

    protected String getTextByXPath(final String xpathPattern, final String... params) {
        return waitUntilOrNull(new ExpectedCondition<String>() {
            @Override
            public String apply(WebDriver webDriver) {
                WebElement element = findByXPath(xpathPattern, params);
                return getElementText(element);
            }
        });
    }

    public String textByClassName(String className) {
        return getTextByClassName(className);
    }

    protected String getTextByClassName(final String className) {
        return waitUntilOrNull(new ExpectedCondition<String>() {
            @Override
            public String apply(WebDriver webDriver) {
                WebElement element = findByClassName(className);
                return getElementText(element);
            }
        });
    }

    protected WebElement findByClassName(String className) {
        By by = By.className(className);
        return getSeleniumHelper().findElement(by);
    }

    protected WebElement findByXPath(String xpathPattern, String... params) {
        By by = getSeleniumHelper().byXpath(xpathPattern, params);
        return getSeleniumHelper().findElement(by);
    }

    protected WebElement findByJavascript(String script, Object... parameters) {
        By by = getSeleniumHelper().byJavascript(script, parameters);
        return getSeleniumHelper().findElement(by);
    }

    protected List<WebElement> findAllByXPath(String xpathPattern, String... params) {
        By by = getSeleniumHelper().byXpath(xpathPattern, params);
        return findElements(by);
    }

    protected List<WebElement> findAllByCss(String cssPattern, String... params) {
        By by = getSeleniumHelper().byCss(cssPattern, params);
        return findElements(by);
    }

    protected List<WebElement> findAllByJavascript(String script, Object... parameters) {
        By by = getSeleniumHelper().byJavascript(script, parameters);
        return findElements(by);
    }

    protected List<WebElement> findElements(By by) {
        return getSeleniumHelper().driver().findElements(by);
    }

    protected String getElementText(WebElement element) {
        String result = null;
        if (element != null) {
            scrollIfNotOnScreen(element);
            result = element.getText();
        }
        return result;
    }

    /**
     * Scrolls browser window so top of element becomes visible.
     * @param element element to scroll to.
     */
    protected void scrollTo(WebElement element) {
        getSeleniumHelper().scrollTo(element);
        if (waitAfterScroll > 0) {
            waitMilliseconds(waitAfterScroll);
        }
    }

    /**
     * Scrolls browser window if element is not currently visible so top of element becomes visible.
     * @param element element to scroll to.
     */
    protected void scrollIfNotOnScreen(WebElement element) {
        if (!element.isDisplayed() || !isElementOnScreen(element)) {
            scrollTo(element);
        }
    }

    /**
     * Checks whether element is in browser's viewport.
     * @param element element to check
     * @return true if element is in browser's viewport (or this could not be determined).
     */
    protected boolean isElementOnScreen(WebElement element) {
        Boolean onScreen = getSeleniumHelper().isElementOnScreen(element);
        return onScreen == null || onScreen.booleanValue();
    }

    /**
     * @return number of seconds waitUntil() will wait at most.
     */
    public int secondsBeforeTimeout() {
        return secondsBeforeTimeout;
    }

    /**
     * @param timeout number of seconds before waitUntil() and waitForJavascriptCallback() throw TimeOutException.
     */
    public void secondsBeforeTimeout(int timeout) {
        secondsBeforeTimeout = timeout;
        int timeoutInMs = timeout * 1000;
        getSeleniumHelper().setPageLoadWait(timeoutInMs);
        getSeleniumHelper().setScriptWait(timeoutInMs);
    }

    public void waitMilliSecondAfterScroll(int msToWait) {
        waitAfterScroll = msToWait;
    }

    public int currentBrowserWidth() {
        return getSeleniumHelper().getWindowSize().getWidth();
    }

    public int currentBrowserHeight() {
        return getSeleniumHelper().getWindowSize().getHeight();
    }

    public void setBrowserWidth(int newWidth) {
        int currentHeight = getSeleniumHelper().getWindowSize().getHeight();
        setBrowserSizeToBy(newWidth, currentHeight);
    }

    public void setBrowserHeight(int newHeight) {
        int currentWidth = getSeleniumHelper().getWindowSize().getWidth();
        setBrowserSizeToBy(currentWidth, newHeight);
    }

    public void setBrowserSizeToBy(int newWidth, int newHeight) {
        getSeleniumHelper().setWindowSize(newWidth, newHeight);
        Dimension actualSize = getSeleniumHelper().getWindowSize();
        if (actualSize.getHeight() != newHeight || actualSize.getWidth() != newWidth) {
            String message = String.format("Unable to change size to: %s x %s; size is: %s x %s",
                    newWidth, newHeight, actualSize.getWidth(), actualSize.getHeight());
            throw new SlimFixtureException(false, message);
        }
    }

    /**
     * @param directory sets base directory where screenshots will be stored.
     */
    public void screenshotBaseDirectory(String directory) {
        if (directory.equals("")
                || directory.endsWith("/")
                || directory.endsWith("\\")) {
            screenshotBase = directory;
        } else {
            screenshotBase = directory + "/";
        }
    }

    /**
     * @param height height to use to display screenshot images
     */
    public void screenshotShowHeight(String height) {
        screenshotHeight = height;
    }

    /**
     * Takes screenshot from current page
     * @param basename filename (below screenshot base directory).
     * @return location of screenshot.
     */
    public String takeScreenshot(String basename) {
        String screenshotFile = createScreenshot(basename);
        if (screenshotFile == null) {
            throw new SlimFixtureException(false, "Unable to take screenshot: does the webdriver support it?");
        } else {
            screenshotFile = getScreenshotLink(screenshotFile);
        }
        return screenshotFile;
    }

    private String getScreenshotLink(String screenshotFile) {
        String wikiUrl = getWikiUrl(screenshotFile);
        if (wikiUrl != null) {
            // make href to screenshot

            if ("".equals(screenshotHeight)) {
                wikiUrl = String.format("<a href=\"%s\">%s</a>",
                        wikiUrl, screenshotFile);
            } else {
                wikiUrl = String.format("<a href=\"%1$s\"><img src=\"%1$s\" title=\"%2$s\" height=\"%3$s\"/></a>",
                        wikiUrl, screenshotFile, screenshotHeight);
            }
            screenshotFile = wikiUrl;
        }
        return screenshotFile;
    }

    private String createScreenshot(String basename) {
        String name = getScreenshotBasename(basename);
        return getSeleniumHelper().takeScreenshot(name);
    }

    private String createScreenshot(String basename, Throwable t) {
        String screenshotFile;
        byte[] screenshotInException = getSeleniumHelper().findScreenshot(t);
        if (screenshotInException == null || screenshotInException.length == 0) {
            screenshotFile = createScreenshot(basename);
        } else {
            String name = getScreenshotBasename(basename);
            screenshotFile = getSeleniumHelper().writeScreenshot(name, screenshotInException);
        }
        return screenshotFile;
    }

    private String getScreenshotBasename(String basename) {
        return screenshotBase + basename;
    }

    /**
     * Waits until the condition evaluates to a value that is neither null nor
     * false. Because of this contract, the return type must not be Void.
     * @param <T> the return type of the method, which must not be Void
     * @param condition condition to evaluate to determine whether waiting can be stopped.
     * @throws SlimFixtureException if condition was not met before secondsBeforeTimeout.
     * @return result of condition.
     */
    protected <T> T waitUntil(ExpectedCondition<T> condition) {
        try {
            return waitUntilImpl(condition);
        } catch (TimeoutException e) {
            String message = getTimeoutMessage(e);
            throw new SlimFixtureException(false, message, e);
        }
    }

    /**
     * Waits until the condition evaluates to a value that is neither null nor
     * false. If that does not occur the whole test is stopped.
     * Because of this contract, the return type must not be Void.
     * @param <T> the return type of the method, which must not be Void
     * @param condition condition to evaluate to determine whether waiting can be stopped.
     * @throws TimeoutStopTestException if condition was not met before secondsBeforeTimeout.
     * @return result of condition.
     */
    protected <T> T waitUntilOrStop(ExpectedCondition<T> condition) {
        try {
            return waitUntilImpl(condition);
        } catch (TimeoutException e) {
            return handleTimeoutException(e);
        }
    }

    /**
     * Waits until the condition evaluates to a value that is neither null nor
     * false. If that does not occur null is returned.
     * Because of this contract, the return type must not be Void.
     * @param <T> the return type of the method, which must not be Void
     * @param condition condition to evaluate to determine whether waiting can be stopped.
     * @return result of condition.
     */
    protected <T> T waitUntilOrNull(ExpectedCondition<T> condition) {
        try {
            return waitUntilImpl(condition);
        } catch (TimeoutException e) {
            return null;
        }
    }

    protected <T> T waitUntilImpl(ExpectedCondition<T> condition) {
        return getSeleniumHelper().waitUntil(secondsBeforeTimeout(), condition);
    }

    protected <T> T handleTimeoutException(TimeoutException e) {
        String message = getTimeoutMessage(e);
        throw new TimeoutStopTestException(false, message, e);
    }

    private String getTimeoutMessage(TimeoutException e) {
        String messageBase = String.format("Timed-out waiting (after %ss)", secondsBeforeTimeout());
        return getSlimFixtureExceptionMessage("timeouts", "timeout", messageBase, e);
    }

    protected String getSlimFixtureExceptionMessage(String screenshotFolder, String screenshotFile, String messageBase, Throwable t) {
        String screenshotBaseName = String.format("%s/%s/%s", screenshotFolder, getClass().getSimpleName(), screenshotFile);
        return getSlimFixtureExceptionMessage(screenshotBaseName, messageBase, t);
    }

    protected String getSlimFixtureExceptionMessage(String screenshotBaseName, String messageBase, Throwable t) {
        // take a screenshot of what was on screen
        String screenShotFile = null;
        try {
            screenShotFile = createScreenshot(screenshotBaseName, t);
        } catch (Exception sse) {
            // unable to take screenshot
            sse.printStackTrace();
        }
        String message = messageBase;
        if (message == null) {
            if (t == null) {
                message = "";
            } else {
                message = ExceptionUtils.getStackTrace(t);
            }
        }
        if (screenShotFile != null) {
            String exceptionMsg = formatExceptionMsg(message);
            message = String.format("<div><div>%s.</div><div>Page content:%s</div></div>",
                    exceptionMsg, getScreenshotLink(screenShotFile));
        }
        return message;
    }

    protected String formatExceptionMsg(String value) {
        return StringEscapeUtils.escapeHtml4(value);
    }

    /**
     * @return helper to use.
     */
    protected final SeleniumHelper getSeleniumHelper() {
        return seleniumHelper;
    }

    /**
     * Sets SeleniumHelper to use, for testing purposes.
     * @param helper helper to use.
     */
    void setSeleniumHelper(SeleniumHelper helper) {
        seleniumHelper = helper;
    }

    protected Object waitForJavascriptCallback(String statement, Object... parameters) {
        try {
            return getSeleniumHelper().waitForJavascriptCallback(statement, parameters);
        } catch (TimeoutException e) {
            return handleTimeoutException(e);
        }
    }
}
