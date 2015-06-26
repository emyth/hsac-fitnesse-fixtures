package nl.hsac.fitnesse.fixture.slim.web;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import nl.hsac.fitnesse.fixture.slim.SlimFixtureException;
import nl.hsac.fitnesse.fixture.util.BinaryHttpResponse;
import nl.hsac.fitnesse.fixture.util.FileUtil;
import nl.hsac.fitnesse.fixture.util.HttpResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.Select;

/**
 * Fixture to test using Selenium (aka with a browser) when content of site is an HTML website.
 * This fixture's methods encapsulate knowledge on how to translate end-user terminology into
 * web elements to interact on using Selenium.
 */
public class BrowserTest extends SeleniumTest {
    private NgBrowserTest ngBrowserTest;
    private String downloadBase = new File(filesDir, "downloads").getPath() + "/";

    @Override
    protected void beforeInvoke(Method method, Object[] arguments) {
        super.beforeInvoke(method, arguments);
        waitForAngularIfNeeded(method);
    }

    /**
     * Determines whether the current method might require waiting for angular given the currently open site,
     * and ensure it does if needed.
     * @param method
     */
    protected void waitForAngularIfNeeded(Method method) {
        if (ngBrowserTest == null) {
            ngBrowserTest = new NgBrowserTest();
        }
        if (ngBrowserTest.requiresWaitForAngular(method) && currentSiteUsesAngular()) {
            ngBrowserTest.waitForAngularRequestsToFinish();
        }
    }

    protected boolean currentSiteUsesAngular() {
        Object windowHasAngular = getSeleniumHelper().executeJavascript("return window.angular?1:0;");
        return Long.valueOf(1).equals(windowHasAngular);
    }

    public BrowserTest() {
        super();
    }

    public BrowserTest(int secondsBeforeTimeout) {
        super(secondsBeforeTimeout);
    }

    @Override
    public boolean open(final String address) {
        boolean result = super.open(address);
        if (result) {
            final String url = getUrl(address);
            waitUntil(new ExpectedCondition<Boolean>() {
                @Override
                public Boolean apply(WebDriver webDriver) {
                    String readyState = getSeleniumHelper().executeJavascript("return document.readyState").toString();
                    // IE 7 is reported to return "loaded"
                    boolean done = "complete".equalsIgnoreCase(readyState) || "loaded".equalsIgnoreCase(readyState);
                    if (!done) {
                        System.err.printf("Open of %s returned while document.readyState was %s", url, readyState);
                        System.err.println();
                    }
                    return done;
                }
            });
        }
        return result;
    }

    @Override
    public boolean back() {
        boolean result = super.back();
        if (result) {
            // firefox sometimes prevents immediate back, if previous page was reached via POST
            waitMilliseconds(500);
            WebElement element = getSeleniumHelper().findElement(By.id("errorTryAgain"));
            if (element != null) {
                element.click();
                // don't use confirmAlert as this may be overridden in subclass and to get rid of the
                // firefox pop-up we need the basic behavior
                getSeleniumHelper().getAlert().accept();
            }
        }
        return result;
    }

    public boolean openInNewTab(String url) {
        String cleanUrl = getUrl(url);
        final int tabCount = tabCount();
        getSeleniumHelper().executeJavascript("window.open('%s', '_blank')", cleanUrl);
        // ensure new window is open
        waitUntil(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                return tabCount() > tabCount;
            }
        });
        return switchToNextTab();
    }

    public String pageTitle() {
        return getSeleniumHelper().getPageTitle();
    }

    /**
     * @return current page's content type.
     */
    public String pageContentType() {
        String result = null;
        Object ct = getSeleniumHelper().executeJavascript("return document.contentType;");
        if (ct != null) {
            result = ct.toString();
        }
        return result;
    }

    /**
     * Replaces content at place by value.
     * @param value value to set.
     * @param place element to set value on.
     * @return true, if element was found.
     */
    public boolean enterAs(String value, String place) {
        return enter(value, place, true);
    }

    /**
     * Adds content to place.
     * @param value value to add.
     * @param place element to add value to.
     * @return true, if element was found.
     */
    public boolean enterFor(String value, String place) {
        return enter(value, place, false);
    }

    protected boolean enter(final String value, final String place, final boolean shouldClear) {
        return waitUntil(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                WebElement element = getElementToSendValue(place);
                boolean result = element != null && isInteractable(element);
                if (result) {
                    if (shouldClear) {
                        element.clear();
                    }
                    sendValue(element, value);
                }
                return result;
            }
        });
    }

    protected WebElement getElementToSendValue(String place) {
        return getElement(place);
    }

    /**
     * Sends Fitnesse cell content to element.
     * @param element element to call sendKeys() on.
     * @param value cell content.
     */
    protected void sendValue(WebElement element, String value) {
        if (StringUtils.isNotEmpty(value)) {
            String keys = cleanupValue(value);
            element.sendKeys(keys);
        }
    }

    public boolean selectAs(String value, String place) {
        return selectFor(value, place);
    }

    public boolean selectFor(final String value, final String place) {
        return waitUntil(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                // choose option for select, if possible
                boolean result = clickSelectOption(place, value);
                if (!result) {
                    // try to click the first element with right value
                    result = click(value);
                }
                return result;
            }
        });
    }

    public boolean enterForHidden(final String value, final String idOrName) {
        return waitUntil(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                return getSeleniumHelper().setHiddenInputValue(idOrName, value);
            }
        });
    }

    private boolean clickSelectOption(String selectPlace, String optionValue) {
        WebElement element = getElementToSelectFor(selectPlace);
        return clickSelectOption(element, optionValue);
    }

    protected WebElement getElementToSelectFor(String selectPlace) {
        return getElement(selectPlace);
    }

    protected boolean clickSelectOption(WebElement element, String optionValue) {
        boolean result = false;
        if (element != null) {
            if (isSelect(element)) {
                By xpath = getSeleniumHelper().byXpath(".//option[normalize-space(text()) = '%s']", optionValue);
                WebElement option = getSeleniumHelper().findElement(element, true, xpath);
                if (option == null) {
                    xpath = getSeleniumHelper().byXpath(".//option[contains(normalize-space(text()), '%s')]", optionValue);
                    option = getSeleniumHelper().findElement(element, true, xpath);
                }
                if (option != null) {
                    result = clickElement(option);
                }
            }
        }
        return result;
    }

    public boolean click(final String place) {
        // if other element hides the element (in Chrome) an exception is thrown
        // we retry clicking the element a few times before giving up.
        return waitUntil(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                boolean result = false;
                try {
                    WebElement element = getElementToClick(place);
                    result = clickElement(element);
                } catch (WebDriverException e) {
                    String msg = e.getMessage();
                    if (msg == null || !msg.contains("Other element would receive the click")) {
                        throw e;
                    }
                }
                return result;
            }
        });
    }

    protected WebElement getElementToClick(String place) {
        WebElement element = getSeleniumHelper().findElement(By.linkText(place));
        WebElement firstFound = element;
        if (!isInteractable(element)) {
            element = getSeleniumHelper().getElementExact(place);
            if (firstFound == null) {
                firstFound = element;
            }
        }
        if (!isInteractable(element)) {
            element = getSeleniumHelper().findElement(By.partialLinkText(place));
            if (firstFound == null) {
                firstFound = element;
            }
        }
        if (!isInteractable(element)) {
            element = getSeleniumHelper().getElementPartial(place);
            if (firstFound == null) {
                firstFound = element;
            }
        }
        if (!isInteractable(element)) {
            // find element with specified text and 'onclick' attribute
            element = findByXPath("//*[@onclick and normalize-space(text())='%s']", place);
            if (firstFound == null) {
                firstFound = element;
            }
        }
        if (!isInteractable(element)) {
            element = findByXPath("//*[@onclick and contains(normalize-space(text()),'%s')]", place);
            if (firstFound == null) {
                firstFound = element;
            }
        }
        if (!isInteractable(element)) {
            // find element with child with specified text and 'onclick' attribute
            element = findByXPath("//*[@onclick and normalize-space(descendant::text())='%s']", place);
            if (firstFound == null) {
                firstFound = element;
            }
        }
        if (!isInteractable(element)) {
            element = findByXPath("//*[@onclick and contains(normalize-space(descendant::text()),'%s')]", place);
            if (firstFound == null) {
                firstFound = element;
            }
        }
        if (!isInteractable(element)) {
            // find element with specified text
            element = findByXPath("//*[normalize-space(text())='%s']", place);
            if (firstFound == null) {
                firstFound = element;
            }
        }
        if (!isInteractable(element)) {
            element = findByXPath("//*[contains(normalize-space(text()),'%s')]", place);
            if (firstFound == null) {
                firstFound = element;
            }
        }
        if (!isInteractable(element)) {
            // find element with child with specified text
            element = findByXPath("//*[normalize-space(descendant::text())='%s']", place);
            if (firstFound == null) {
                firstFound = element;
            }
        }
        if (!isInteractable(element)) {
            element = findByXPath("//*[contains(normalize-space(descendant::text()),'%s')]", place);
            if (firstFound == null) {
                firstFound = element;
            }
        }
        return isInteractable(element)
                ? element
                : firstFound;
    }

    public boolean waitForPage(final String pageName) {
        return waitUntilOrStop(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                return pageTitle().equals(pageName);
            }
        });
    }

    protected boolean hasText(WebElement element, String textToLookFor) {
        boolean ok;
        try {
            ok = hasTextUnsafe(element, textToLookFor);
        } catch (StaleElementReferenceException e) {
            // element detached from DOM
            ok = false;
        } catch (WebDriverException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Element does not exist in cache")) {
                // stale element Safari style
                ok = false;
            } else {
                throw e;
            }
        }
        return ok;
    }

    public String valueOf(String place) {
        return valueFor(place);
    }

    public String valueFor(final String place) {
        return waitUntilOrNull(new ExpectedCondition<String>() {
            @Override
            public String apply(WebDriver webDriver) {
                WebElement element = getElementToRetrieveValue(place);
                return valueFor(element);
            }
        });
    }

    protected WebElement getElementToRetrieveValue(String place) {
        return getElement(place);
    }

    protected String valueFor(WebElement element) {
        String result = null;
        if (element != null) {
            if (isSelect(element)) {
                Select s = new Select(element);
                List<WebElement> options = s.getAllSelectedOptions();
                if (options.size() > 0) {
                    result = getElementText(options.get(0));
                }
            } else {
                if ("checkbox".equals(element.getAttribute("type"))) {
                    result = String.valueOf("true".equals(element.getAttribute("checked")));
                } else {
                    result = element.getAttribute("value");
                    if (result == null) {
                        scrollIfNotOnScreen(element);
                        result = element.getText();
                    }
                }
            }
        }
        return result;
    }

    private boolean isSelect(WebElement element) {
        return "select".equalsIgnoreCase(element.getTagName());
    }

    public boolean clear(final String place) {
        return waitUntil(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                boolean result = false;
                WebElement element = getElementToClear(place);
                if (element != null) {
                    element.clear();
                    result = true;
                }
                return result;
            }
        });
    }

    protected WebElement getElementToClear(String place) {
        return getElementToSendValue(place);
    }

    public boolean enterAsInRowWhereIs(final String value, final String requestedColumnName, final String selectOnColumn, final String selectOnValue) {
        return waitUntil(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                String columnXPath = getXPathForColumnInRowByValueInOtherColumn(selectOnColumn, selectOnValue);
                String requestedIndex = getXPathForColumnIndex(requestedColumnName);
                WebElement element = findByXPath("%s[%s]//input", columnXPath, requestedIndex);
                if (element == null) {
                    element = findByXPath("%s[%s]//textarea", columnXPath, requestedIndex);
                }
                boolean result = element != null && isInteractable(element);
                if (result) {
                    element.clear();
                    sendValue(element, value);
                }
                return result;
            }
        });
    }

    public String valueOfColumnNumberInRowNumber(int columnIndex, int rowIndex) {
        return getTextByXPath("(//tr[boolean(td)])[%s]/td[%s]", Integer.toString(rowIndex), Integer.toString(columnIndex));
    }

    public String valueOfInRowNumber(String requestedColumnName, int rowIndex) {
        String columnXPath = String.format("(//tr[boolean(td)])[%s]/td", rowIndex);
        return valueInRow(columnXPath, requestedColumnName);
    }

    public String valueOfInRowWhereIs(String requestedColumnName, String selectOnColumn, String selectOnValue) {
        String columnXPath = getXPathForColumnInRowByValueInOtherColumn(selectOnColumn, selectOnValue);
        return valueInRow(columnXPath, requestedColumnName);
    }

    protected String valueInRow(String columnXPath, String requestedColumnName) {
        String requestedIndex = getXPathForColumnIndex(requestedColumnName);
        return getTextByXPath("%s[%s]", columnXPath, requestedIndex);
    }

    public boolean rowExistsWhereIs(String selectOnColumn, String selectOnValue) {
        final String columnXPath = getXPathForColumnInRowByValueInOtherColumn(selectOnColumn, selectOnValue);
        WebElement element = waitUntilOrNull(new ExpectedCondition<WebElement>() {
            @Override
            public WebElement apply(WebDriver webDriver) {
                return findByXPath(columnXPath);
            }
        });
        return element != null;
    }

    public boolean clickInRowNumber(String place, int rowIndex) {
        String columnXPath = String.format("(//tr[boolean(td)])[%s]/td", rowIndex);
        return clickInRow(columnXPath, place);
    }

    public boolean clickInRowWhereIs(String place, String selectOnColumn, String selectOnValue) {
        String columnXPath = getXPathForColumnInRowByValueInOtherColumn(selectOnColumn, selectOnValue);
        return clickInRow(columnXPath, place);
    }

    protected boolean clickInRow(final String columnXPath, final String place) {
        return waitUntil(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                boolean result = false;
                // find an input to click in the row
                WebElement element = findByXPath("%s//input[contains(@value, '%s')]", columnXPath, place);
                if (element == null) {
                    // see whether there is an element with the specified place as text() in the row
                    element = findByXPath("%s//*[contains(normalize-space(text()),'%s')]", columnXPath, place);
                    if (element == null) {
                        // find an element to click in the row by its title (aka tooltip)
                        element = findByXPath("%s//*[contains(@title, '%s')]", columnXPath, place);
                    }
                }
                if (element != null) {
                    result = clickElement(element);
                }
                return result;
            }
        });
    }

    /**
     * Downloads the target of a link in a grid's row.
     * @param place which link to download.
     * @param rowNumber (1-based) row number to retrieve link from.
     * @return downloaded file if any, null otherwise.
     */
    public String downloadFromRowNumber(String place, int rowNumber) {
        String columnXPath = String.format("(//tr[boolean(td)])[%s]/td", rowNumber);
        return downloadFromRow(columnXPath, place);
    }

    /**
     * Downloads the target of a link in a grid, finding the row based on one of the other columns' value.
     * @param place which link to download.
     * @param selectOnColumn column header of cell whose value must be selectOnValue.
     * @param selectOnValue value to be present in selectOnColumn to find correct row.
     * @return downloaded file if any, null otherwise.
     */
    public String downloadFromRowWhereIs(String place, String selectOnColumn, String selectOnValue) {
        String columnXPath = getXPathForColumnInRowByValueInOtherColumn(selectOnColumn, selectOnValue);
        return downloadFromRow(columnXPath, place);
    }

    protected String downloadFromRow(final String columnXPath, final String place) {
        String result = waitUntil(new ExpectedCondition<String>() {
            @Override
            public String apply(WebDriver webDriver) {
                String result = null;
                // find an a to download from based on its text()
                WebElement element = findByXPath("%s//a[contains(normalize-space(text()),'%s')]", columnXPath, place);
                if (element == null) {
                    // find an a to download based on its column header
                    String requestedIndex = getXPathForColumnIndex(place);
                    element = findByXPath("%s[%s]//a", columnXPath, requestedIndex);
                    if (element == null) {
                        // find an a to download in the row by its title (aka tooltip)
                        element = findByXPath("%s//a[contains(@title, '%s')]", columnXPath, place);
                    }
                }
                if (element != null) {
                    result = downloadLinkTarget(element);
                }
                return result;
            }
        });
        return result;
    }

    /**
     * Creates an XPath expression that will find a cell in a row, selecting the row based on the
     * text in a specific column (identified by its header text).
     * @param columnName header text of the column to find value in.
     * @param value text to find in column with the supplied header.
     * @return XPath expression selecting a td in the row
     */
    protected String getXPathForColumnInRowByValueInOtherColumn(String columnName, String value) {
        String selectIndex = getXPathForColumnIndex(columnName);
        return String.format("//tr[td[%s]/descendant-or-self::text()[normalize-space(.)='%s']]/td", selectIndex, value);
    }

    /**
     * Creates an XPath expression that will determine, for a row, which index to use to select the cell in the column
     * with the supplied header text value.
     * @param columnName name of column in header (th)
     * @return XPath expression which can be used to select a td in a row
     */
    protected String getXPathForColumnIndex(String columnName) {
        // determine how many columns are before the column with the requested name
        // the column with the requested name will have an index of the value +1 (since XPath indexes are 1 based)
        return String.format("count(ancestor::table[1]//tr/th/descendant-or-self::text()[normalize-space(.)='%s']/ancestor-or-self::th[1]/preceding-sibling::th)+1", columnName);
    }

    protected WebElement getElement(String place) {
        return getSeleniumHelper().getElement(place);
    }

    /**
     * Scrolls browser window so top of place becomes visible.
     * @param place element to scroll to.
     */
    public void scrollTo(final String place) {
        waitUntil(new ExpectedCondition<WebElement>() {
            @Override
            public WebElement apply(WebDriver webDriver) {
                WebElement element = getElementToScrollTo(place);
                if (element != null) {
                    scrollTo(element);
                }
                return element;
            }
        });
    }

    protected WebElement getElementToScrollTo(String place) {
        return getElementToCheckVisibility(place);
    }

    /**
     * Determines whether element can be see in browser's window.
     * @param place element to check.
     * @return true if element is displayed and in viewport.
     */
    public boolean isVisible(final String place) {
        Boolean r = waitUntilOrNull(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                Boolean result = null;
                WebElement element = getElementToCheckVisibility(place);
                if (element != null) {
                    result = element.isDisplayed() && isElementOnScreen(element);
                }
                return result;
            }
        });
        return r != null && r.booleanValue();
    }

    protected WebElement getElementToCheckVisibility(String place) {
        return getElementToClick(place);
    }

    /**
     * Clears HTML5's localStorage (for the domain of the current open page in the browser).
     */
    public void clearLocalStorage() {
        getSeleniumHelper().executeJavascript("localStorage.clear();");
    }

    /**
     * Deletes all cookies(for the domain of the current open page in the browser).
     */
    public void deleteAllCookies() {
        getSeleniumHelper().deleteAllCookies();
    }

    /**
     * Downloads the target of the supplied link.
     * @param place link to follow.
     * @return downloaded file if any, null otherwise.
     */
    public String download(final String place) {
        return waitUntil(new ExpectedCondition<String>() {
            @Override
            public String apply(WebDriver webDriver) {
                By selector = By.linkText(place);
                WebElement element = getSeleniumHelper().findElement(selector);
                if (element == null) {
                    selector = By.partialLinkText(place);
                    element = getSeleniumHelper().findElement(selector);
                    if (element == null) {
                        selector = By.id(place);
                        element = getSeleniumHelper().findElement(selector);
                        if (element == null) {
                            selector = By.name(place);
                            element = getSeleniumHelper().findElement(selector);
                        }
                    }
                }
                return downloadLinkTarget(element);
            }
        });
    }

    /**
     * Downloads the target of the supplied link.
     * @param element link to follow.
     * @return downloaded file if any, null otherwise.
     */
    protected String downloadLinkTarget(WebElement element) {
        String result = null;
        if (element != null) {
            String href = element.getAttribute("href");
            if (href != null) {
                result = downloadContentFrom(href);
            } else {
                throw new SlimFixtureException(false, "Could not determine url to download from");
            }
        }
        return result;
    }

    /**
     * Downloads binary content from specified url (using the browser's cookies).
     * @param urlOrLink url to download from
     * @return link to downloaded file
     */
    public String downloadContentFrom(String urlOrLink) {
        String result = null;
        if (urlOrLink != null) {
            String url = getUrl(urlOrLink);
            BinaryHttpResponse resp = new BinaryHttpResponse();
            getUrlContent(url, resp);
            byte[] content = resp.getResponseContent();
            if (content == null) {
                result = resp.getResponse();
            } else {
                String fileName = resp.getFileName();
                String baseName = FilenameUtils.getBaseName(fileName);
                String ext = FilenameUtils.getExtension(fileName);
                String downloadedFile = FileUtil.saveToFile(getDownloadName(baseName), ext, content);
                String wikiUrl = getWikiUrl(downloadedFile);
                if (wikiUrl != null) {
                    // make href to file
                    result = String.format("<a href=\"%s\">%s</a>", wikiUrl, fileName);
                } else {
                    result = downloadedFile;
                }
            }
        }
        return result;
    }

    /**
     * Selects a file using a file upload control.
     * @param fileName file to upload
     * @param place file input to select the file for
     * @return true, if place was a file input and file existed.
     */
    public boolean selectFileFor(String fileName, final String place) {
        boolean result = false;
        if (fileName != null) {
            final String fullPath = getFilePathFromWikiUrl(fileName);
            if (new File(fullPath).exists()) {
                result = waitUntil(new ExpectedCondition<Boolean>() {
                    @Override
                    public Boolean apply(WebDriver webDriver) {
                        boolean result = false;
                        WebElement element = getElementToSelectFile(place);
                        if (element != null) {
                            element.sendKeys(fullPath);
                            result = true;
                        }
                        return result;
                    }
                });
            } else {
                throw new SlimFixtureException(false, "Unable to find file: " + fullPath);
            }
        }
        return result;
    }

    protected WebElement getElementToSelectFile(String place) {
        WebElement result = null;
        WebElement element = getElement(place);
        if (element != null
                && "input".equalsIgnoreCase(element.getTagName())
                && "file".equalsIgnoreCase(element.getAttribute("type"))) {
            result = element;
        }
        return result;
    }

    private String getDownloadName(String baseName) {
        return downloadBase + baseName;
    }

    /**
     * GETs content of specified URL, using the browsers cookies.
     * @param url url to retrieve content from
     * @param resp response to store content in
     */
    protected void getUrlContent(String url, HttpResponse resp) {
        Set<Cookie> browserCookies = getSeleniumHelper().getCookies();
        BasicCookieStore cookieStore = new BasicCookieStore();
        for (Cookie browserCookie : browserCookies) {
            BasicClientCookie cookie = convertCookie(browserCookie);
            cookieStore.addCookie(cookie);
        }
        resp.setCookieStore(cookieStore);
        getEnvironment().doGet(url, resp);
    }

    private BasicClientCookie convertCookie(Cookie browserCookie) {
        BasicClientCookie cookie = new BasicClientCookie(browserCookie.getName(), browserCookie.getValue());
        cookie.setDomain(browserCookie.getDomain());
        cookie.setPath(browserCookie.getPath());
        cookie.setExpiryDate(browserCookie.getExpiry());
        return cookie;
    }

    public NgBrowserTest getNgBrowserTest() {
        return ngBrowserTest;
    }

    public void setNgBrowserTest(NgBrowserTest ngBrowserTest) {
        this.ngBrowserTest = ngBrowserTest;
    }
}
