package ai.platon.pulsar.crawl

import ai.platon.pulsar.common.Strings
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.urls.UrlAware
import ai.platon.pulsar.crawl.fetch.FetchResult
import ai.platon.pulsar.crawl.fetch.driver.WebDriver
import ai.platon.pulsar.crawl.fetch.privacy.PrivacyContext
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.persist.PageDatum
import ai.platon.pulsar.persist.WebPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.random.Random

interface EventHandler {
    val name: String
}

abstract class AbstractEventHandler: EventHandler {
    override val name: String = ""
}

interface EventHandlerPipeline {
    val size: Int
    val isEmpty: Boolean get() = size == 0
    val isNotEmpty: Boolean get() = !isEmpty
}

abstract class VoidEventHandler: () -> Unit, AbstractEventHandler() {
    abstract override operator fun invoke()
}

abstract class UrlAwareHandler: (UrlAware) -> Unit, AbstractEventHandler() {
    abstract override operator fun invoke(url: UrlAware)
}

abstract class UrlAwareFilter: (UrlAware) -> UrlAware?, AbstractEventHandler() {
    abstract override operator fun invoke(url: UrlAware): UrlAware?
}

abstract class UrlHandler: (String) -> Unit, AbstractEventHandler() {
    abstract override operator fun invoke(url: String)
}

abstract class UrlFilter: (String) -> String?, AbstractEventHandler() {
    abstract override operator fun invoke(url: String): String?
}

abstract class WebPageHandler: (WebPage) -> Unit, AbstractEventHandler() {
    abstract override operator fun invoke(page: WebPage)
}

abstract class UrlAwareWebPageHandler: (UrlAware, WebPage?) -> Unit, AbstractEventHandler() {
    abstract override operator fun invoke(url: UrlAware, page: WebPage?)
}

abstract class HtmlDocumentHandler: (WebPage, FeaturedDocument) -> Unit, AbstractEventHandler() {
    abstract override operator fun invoke(page: WebPage, document: FeaturedDocument)
}

abstract class FetchResultHandler: (FetchResult) -> Unit, AbstractEventHandler() {
    abstract override operator fun invoke(page: FetchResult)
}

abstract class WebPageBatchHandler: (Iterable<WebPage>) -> Unit, AbstractEventHandler() {
    abstract override operator fun invoke(pages: Iterable<WebPage>)
}

abstract class FetchResultBatchHandler: (Iterable<FetchResult>) -> Unit, AbstractEventHandler() {
    abstract override operator fun invoke(pages: Iterable<FetchResult>)
}

abstract class PrivacyContextHandler: (PrivacyContext) -> Unit, AbstractEventHandler() {
    abstract override operator fun invoke(privacyContext: PrivacyContext)
}

abstract class WebDriverHandler: (WebDriver) -> Any?, AbstractEventHandler() {
    abstract override operator fun invoke(driver: WebDriver): Any?
}

abstract class WebPageWebDriverHandler: (WebPage, WebDriver) -> Any?, AbstractEventHandler() {
    abstract override operator fun invoke(page: WebPage, driver: WebDriver): Any?
    abstract suspend fun invokeDeferred(page: WebPage, driver: WebDriver): Any?
}

abstract class WebDriverFetchResultHandler: (WebPage, WebDriver) -> FetchResult?, AbstractEventHandler() {
    abstract override operator fun invoke(page: WebPage, driver: WebDriver): FetchResult?
    abstract suspend fun invokeDeferred(page: WebPage, driver: WebDriver): FetchResult?
}

class VoidEventHandlerPipeline: VoidEventHandler(), EventHandlerPipeline {
    private val registeredHandlers = Collections.synchronizedList(mutableListOf<() -> Unit>())

    override val size: Int
        get() = registeredHandlers.size

    fun addFirst(handler: () -> Unit): VoidEventHandlerPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addLast(handler: () -> Unit): VoidEventHandlerPipeline {
        registeredHandlers.add(handler)
        return this
    }

    override operator fun invoke() {
        registeredHandlers.forEach { it() }
    }
}

class UrlAwareHandlerPipeline: UrlAwareHandler(), EventHandlerPipeline {
    private val registeredHandlers = Collections.synchronizedList(mutableListOf<(UrlAware) -> Unit>())

    override val size: Int
        get() = registeredHandlers.size

    fun addFirst(handler: (UrlAware) -> Unit): UrlAwareHandlerPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addFirst(vararg handlers: (UrlAware) -> Unit): UrlAwareHandlerPipeline {
        handlers.forEach { addFirst(it) }
        return this
    }

    fun addLast(handler: (UrlAware) -> Unit): UrlAwareHandlerPipeline {
        registeredHandlers.add(handler)
        return this
    }

    fun addLast(vararg handlers: (UrlAware) -> Unit): UrlAwareHandlerPipeline {
        handlers.toCollection(registeredHandlers)
        return this
    }

    override operator fun invoke(url: UrlAware) {
        registeredHandlers.forEach { it(url) }
    }
}

class UrlAwareFilterPipeline: UrlAwareFilter(), EventHandlerPipeline {
    private val registeredHandlers = Collections.synchronizedList(mutableListOf<(UrlAware) -> UrlAware?>())

    override val size: Int
        get() = registeredHandlers.size

    fun addFirst(handler: (UrlAware) -> UrlAware?): UrlAwareFilterPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addFirst(vararg handlers: (UrlAware) -> UrlAware?): UrlAwareFilterPipeline {
        handlers.forEach { addFirst(it) }
        return this
    }

    fun addLast(handler: (UrlAware) -> UrlAware?): UrlAwareFilterPipeline {
        registeredHandlers.add(handler)
        return this
    }

    fun addLast(vararg handlers: (UrlAware) -> UrlAware?): UrlAwareFilterPipeline {
        handlers.toCollection(registeredHandlers)
        return this
    }

    override operator fun invoke(url: UrlAware): UrlAware? {
        var result: UrlAware? = url
        registeredHandlers.forEach {
            result = it(url)
        }
        return result
    }
}

class UrlFilterPipeline: UrlFilter(), EventHandlerPipeline {
    private val registeredHandlers = Collections.synchronizedList(mutableListOf<(String) -> String?>())

    override val size: Int
        get() = registeredHandlers.size

    fun addFirst(handler: UrlFilter): UrlFilterPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addFirst(handler: (String) -> String?): UrlFilterPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addLast(handler: (String) -> String?): UrlFilterPipeline {
        registeredHandlers.add(handler)
        return this
    }

    fun remove(handler: (String) -> Unit) {
        registeredHandlers.removeIf { it == handler }
    }

    override operator fun invoke(url: String): String? {
        var result: String? = url
        registeredHandlers.forEach {
            result = it(url)
        }
        return result
    }
}

class UrlHandlerPipeline: UrlHandler(), EventHandlerPipeline {
    private val registeredHandlers = Collections.synchronizedList(mutableListOf<(String) -> Unit>())

    override val size: Int
        get() = registeredHandlers.size

    fun addFirst(handler: (String) -> Unit): UrlHandlerPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addLast(handler: (String) -> Unit): UrlHandlerPipeline {
        registeredHandlers.add(handler)
        return this
    }

    fun remove(handler: (String) -> Unit) {
        registeredHandlers.removeIf { it == handler }
    }

    override operator fun invoke(url: String) {
        registeredHandlers.forEach { it(url) }
    }
}

class WebPageHandlerPipeline: WebPageHandler(), EventHandlerPipeline {
    private val registeredHandlers = Collections.synchronizedList(mutableListOf<(WebPage) -> Unit>())

    override val size: Int
        get() = registeredHandlers.size

    fun addFirst(handler: (WebPage) -> Unit): WebPageHandlerPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addLast(handler: (WebPage) -> Unit): WebPageHandlerPipeline {
        registeredHandlers += object: WebPageHandler() {
            override fun invoke(page: WebPage) = handler(page)
        }
        return this
    }

    fun remove(handler: (WebPage) -> Unit) {
        registeredHandlers.removeIf { it == handler }
    }

    override operator fun invoke(page: WebPage) {
        registeredHandlers.forEach { it(page) }
    }
}

class UrlAwareWebPageHandlerPipeline: UrlAwareWebPageHandler(), EventHandlerPipeline {
    private val registeredHandlers = Collections.synchronizedList(mutableListOf<(UrlAware, WebPage?) -> Unit>())

    override val size: Int
        get() = registeredHandlers.size

    fun addFirst(handler: (UrlAware, WebPage?) -> Unit): UrlAwareWebPageHandlerPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addFirst(vararg handlers: (UrlAware, WebPage?) -> Unit): UrlAwareWebPageHandlerPipeline {
        handlers.forEach { addFirst(it) }
        return this
    }

    fun addLast(handler: (UrlAware, WebPage?) -> Unit): UrlAwareWebPageHandlerPipeline {
        registeredHandlers.add(handler)
        return this
    }

    fun addLast(vararg handlers: (UrlAware, WebPage?) -> Unit): UrlAwareWebPageHandlerPipeline {
        handlers.toCollection(registeredHandlers)
        return this
    }

    fun remove(handler: (UrlAware, WebPage?) -> Unit) {
        registeredHandlers.removeIf { it == handler }
    }

    override operator fun invoke(url: UrlAware, page: WebPage?) {
        registeredHandlers.forEach { it(url, page) }
    }
}

class HtmlDocumentHandlerPipeline: (WebPage, FeaturedDocument) -> Unit, EventHandlerPipeline {
    private val registeredHandlers = Collections.synchronizedList(mutableListOf<(WebPage, FeaturedDocument) -> Unit>())

    override val size: Int
        get() = registeredHandlers.size

    fun addFirst(handler: (WebPage, FeaturedDocument) -> Unit): HtmlDocumentHandlerPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addLast(handler: (WebPage, FeaturedDocument) -> Unit): HtmlDocumentHandlerPipeline {
        registeredHandlers.add(handler)
        return this
    }

    fun remove(handler: (WebPage, FeaturedDocument) -> Unit) {
        registeredHandlers.removeIf { it == handler }
    }

    override fun invoke(page: WebPage, document: FeaturedDocument) {
        registeredHandlers.forEach { it(page, document) }
    }
}

class WebDriverHandlerPipeline: (WebDriver) -> Any?, EventHandlerPipeline {
    private val registeredHandlers = Collections.synchronizedList(mutableListOf<(WebDriver) -> Any?>())

    override val size: Int
        get() = registeredHandlers.size

    fun addFirst(handler: (WebDriver) -> Any?): WebDriverHandlerPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addLast(handler: (WebDriver) -> Any?): WebDriverHandlerPipeline {
        registeredHandlers.add(handler)
        return this
    }

    fun remove(handler: (WebDriver) -> Any?) {
        registeredHandlers.removeIf { it == handler }
    }

    override operator fun invoke(driver: WebDriver) {
        registeredHandlers.forEach { it(driver) }
    }
}

interface LoadEventHandler {
    val onFilter: UrlFilterPipeline

    val onNormalize: UrlFilterPipeline

    val onWillLoad: UrlHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onWillLoad"))
    val onBeforeLoad: UrlHandlerPipeline get() = onWillLoad

    val onWillFetch: WebPageHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onWillFetch"))
    val onBeforeFetch: WebPageHandlerPipeline get() = onWillFetch

    val onWillLaunchBrowser: WebPageHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onWillBrowserLaunch"))
    val onBeforeBrowserLaunch: WebPageHandlerPipeline get() = onWillLaunchBrowser

    val onBrowserLaunched: WebPageWebDriverHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onBrowserLaunched"))
    val onAfterBrowserLaunch: WebPageWebDriverHandlerPipeline get() = onBrowserLaunched

    val onFetched: WebPageHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onFetched"))
    val onAfterFetch: WebPageHandlerPipeline get() = onFetched

    val onWillParse: WebPageHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onWillParseHTMLDocument"))
    val onBeforeParse: WebPageHandlerPipeline get() = onWillParse

    val onWillParseHTMLDocument: WebPageHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onWillParseHTMLDocument"))
    val onBeforeHtmlParse: WebPageHandlerPipeline get() = onWillParseHTMLDocument

    val onWillExtract: WebPageHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onWillExtract"))
    val onBeforeExtract: WebPageHandlerPipeline get() = onWillExtract

    val onExtracted: HtmlDocumentHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onExtracted"))
    val onAfterExtract: HtmlDocumentHandlerPipeline get() = onExtracted

    val onHTMLDocumentParsed: HtmlDocumentHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onHTMLDocumentParsed"))
    val onAfterHtmlParse: HtmlDocumentHandlerPipeline get() = onHTMLDocumentParsed

    val onParsed: WebPageHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onParsed"))
    val onAfterParse: WebPageHandlerPipeline get() = onParsed

    val onLoaded: WebPageHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onLoaded"))
    val onAfterLoad: WebPageHandlerPipeline get() = onLoaded

    fun combine(other: LoadEventHandler): LoadEventHandler
}

abstract class AbstractLoadEventHandler(
    override val onFilter: UrlFilterPipeline = UrlFilterPipeline(),
    override val onNormalize: UrlFilterPipeline = UrlFilterPipeline(),
    override val onWillLoad: UrlHandlerPipeline = UrlHandlerPipeline(),
    override val onWillFetch: WebPageHandlerPipeline = WebPageHandlerPipeline(),
    override val onWillLaunchBrowser: WebPageHandlerPipeline = WebPageHandlerPipeline(),
    override val onBrowserLaunched: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline(),
    override val onFetched: WebPageHandlerPipeline = WebPageHandlerPipeline(),
    override val onWillParse: WebPageHandlerPipeline = WebPageHandlerPipeline(),
    override val onWillParseHTMLDocument: WebPageHandlerPipeline = WebPageHandlerPipeline(),
    override val onWillExtract: WebPageHandlerPipeline = WebPageHandlerPipeline(),
    override val onExtracted: HtmlDocumentHandlerPipeline = HtmlDocumentHandlerPipeline(),
    override val onHTMLDocumentParsed: HtmlDocumentHandlerPipeline = HtmlDocumentHandlerPipeline(),
    override val onParsed: WebPageHandlerPipeline = WebPageHandlerPipeline(),
    override val onLoaded: WebPageHandlerPipeline = WebPageHandlerPipeline()
): LoadEventHandler {

    override fun combine(other: LoadEventHandler): AbstractLoadEventHandler {
        onFilter.addLast(other.onFilter)
        onNormalize.addLast(other.onNormalize)
        onWillLoad.addLast(other.onWillLoad)
        onWillFetch.addLast(other.onWillFetch)
        onWillLaunchBrowser.addLast(other.onWillLaunchBrowser)
        onBrowserLaunched.addLast(other.onBrowserLaunched)
        onFetched.addLast(other.onFetched)
        onWillParse.addLast(other.onWillParse)
        onWillParseHTMLDocument.addLast(other.onWillParseHTMLDocument)
        onWillExtract.addLast(other.onWillExtract)
        onExtracted.addLast(other.onExtracted)
        onHTMLDocumentParsed.addLast(other.onHTMLDocumentParsed)
        onParsed.addLast(other.onParsed)
        onLoaded.addLast(other.onLoaded)

        return this
    }
}

open class DefaultLoadEventHandler: AbstractLoadEventHandler()

abstract class AbstractWebDriverHandler: WebDriverHandler() {
    private val logger = getLogger(AbstractWebDriverHandler::class)

    open val delayPolicy: (String) -> Long get() = { type ->
        when (type) {
            "click" -> 500L + Random.nextInt(500)
            "type" -> 500L + Random.nextInt(500)
            else -> 100L + Random.nextInt(500)
        }
    }

    open var verbose = false

    override val name: String = ""

    override fun invoke(driver: WebDriver): Any? {
        return runBlocking { invokeDeferred(driver) }
    }

    abstract suspend fun invokeDeferred(driver: WebDriver): Any?

    protected suspend fun evaluate(driver: WebDriver, expressions: Iterable<String>): Any? {
        var value: Any? = null
        val validExpressions = expressions
            .mapNotNull { it.trim().takeIf { it.isNotBlank() } }
            .filterNot { it.startsWith("// ") }
        validExpressions.forEach {
            logger.takeIf { verbose }?.info("Evaluate expression >>>$it<<<")
            val v = evaluate(driver, it)
            if (v is String) {
                val s = Strings.stripNonPrintableChar(v)
                logger.takeIf { verbose }?.info("Result >>>$s<<<")
            } else if (v is Int || v is Long) {
                logger.takeIf { verbose }?.info("Result >>>$v<<<")
            }
            value = v
        }
        return value
    }

    protected suspend fun evaluate(driver: WebDriver, expression: String): Any? {
        delayPolicy("evaluate").takeIf { it > 0 }?.let { delay(it) }
        return driver.evaluate(expression)
    }
}

abstract class AbstractWebPageWebDriverHandler: WebPageWebDriverHandler() {
    private val logger = getLogger(AbstractWebPageWebDriverHandler::class)

    open val delayPolicy: (String) -> Long get() = { type ->
        when (type) {
            "click" -> 500L + Random.nextInt(500)
            "type" -> 500L + Random.nextInt(500)
            else -> 100L + Random.nextInt(500)
        }
    }

    open var verbose = false

    override val name: String = ""

    override fun invoke(page: WebPage, driver: WebDriver): Any? {
        return runBlocking { invokeDeferred(page, driver) }
    }

    protected suspend fun evaluate(driver: WebDriver, expressions: Iterable<String>): Any? {
        var value: Any? = null
        val validExpressions = expressions
            .mapNotNull { it.trim().takeIf { it.isNotBlank() } }
            .filterNot { it.startsWith("// ") }
        validExpressions.forEach {
            logger.takeIf { verbose }?.info("Evaluate expression >>>$it<<<")
            val v = evaluate(driver, it)
            if (v is String) {
                val s = Strings.stripNonPrintableChar(v)
                logger.takeIf { verbose }?.info("Result >>>$s<<<")
            } else if (v is Int || v is Long) {
                logger.takeIf { verbose }?.info("Result >>>$v<<<")
            }
            value = v
        }
        return value
    }

    protected suspend fun evaluate(driver: WebDriver, expression: String): Any? {
        delayPolicy("evaluate").takeIf { it > 0 }?.let { delay(it) }
        return driver.evaluate(expression)
    }
}

open class EmptyWebDriverHandler: AbstractWebPageWebDriverHandler() {
    override suspend fun invokeDeferred(page: WebPage, driver: WebDriver): Any? {
        return null
    }
}

interface SimulateEventHandler {
    @Deprecated("Old fashioned name", ReplaceWith("onWillFetch"))
    val onBeforeFetch: WebPageWebDriverHandlerPipeline get() = onWillFetch
    val onWillFetch: WebPageWebDriverHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onFetched"))
    val onAfterFetch: WebPageWebDriverHandlerPipeline get() = onFetched
    val onFetched: WebPageWebDriverHandlerPipeline

    @Deprecated("Old fashioned name", ReplaceWith("onWillNavigate"))
    val onBeforeNavigate: WebPageWebDriverHandlerPipeline get() = onWillNavigate
    val onWillNavigate: WebPageWebDriverHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onNavigated"))
    val onAfterNavigate: WebPageWebDriverHandlerPipeline get() = onNavigated
    val onNavigated: WebPageWebDriverHandlerPipeline

    @Deprecated("Old fashioned name", ReplaceWith("onWillCheckDOMState"))
    val onBeforeCheckDOMState: WebPageWebDriverHandlerPipeline get() = onWillCheckDOMState
    val onWillCheckDOMState: WebPageWebDriverHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onDOMStateChecked"))
    val onAfterCheckDOMState: WebPageWebDriverHandlerPipeline get() = onDOMStateChecked
    val onDOMStateChecked: WebPageWebDriverHandlerPipeline

    @Deprecated("Old fashioned name", ReplaceWith("onWillComputeFeature"))
    val onBeforeComputeFeature: WebPageWebDriverHandlerPipeline get() = onWillComputeFeature
    val onWillComputeFeature: WebPageWebDriverHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onFeatureComputed"))
    val onAfterComputeFeature: WebPageWebDriverHandlerPipeline get() = onFeatureComputed
    val onFeatureComputed: WebPageWebDriverHandlerPipeline

    val onWillInteract: WebPageWebDriverHandlerPipeline
    val onDidInteract: WebPageWebDriverHandlerPipeline

    val onWillStopTab: WebPageWebDriverHandlerPipeline
    val onTabStopped: WebPageWebDriverHandlerPipeline

    fun combine(other: SimulateEventHandler): SimulateEventHandler
}

abstract class AbstractSimulateEventHandler: SimulateEventHandler {
    open val delayPolicy: (String) -> Long get() = { type ->
        when (type) {
            "click" -> 500L + Random.nextInt(500)
            "type" -> 500L + Random.nextInt(500)
            else -> 100L + Random.nextInt(500)
        }
    }

    open var verbose = false

    protected suspend fun smartDelay() = delay(delayPolicy(""))

    protected suspend fun smartDelay(type: String) = delay(delayPolicy(type))

    override val onWillFetch: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline()
    override val onFetched: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline()

    override val onWillNavigate: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline()
    override val onNavigated: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline()

    override val onWillCheckDOMState: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline()
    override val onDOMStateChecked: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline()

    override val onWillComputeFeature: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline()
    override val onFeatureComputed: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline()

    override val onWillInteract: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline()
    override val onDidInteract: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline()

    override val onWillStopTab: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline()
    override val onTabStopped: WebPageWebDriverHandlerPipeline = WebPageWebDriverHandlerPipeline()

    override fun combine(other: SimulateEventHandler): SimulateEventHandler {
        onWillFetch.addLast(other.onWillFetch)
        onFetched.addLast(other.onFetched)

        onWillNavigate.addLast(other.onWillNavigate)
        onNavigated.addLast(other.onNavigated)

        onWillCheckDOMState.addLast(other.onWillCheckDOMState)
        onDOMStateChecked.addLast(other.onDOMStateChecked)
        onWillComputeFeature.addLast(other.onWillComputeFeature)
        onFeatureComputed.addLast(other.onFeatureComputed)

        onWillInteract.addLast(other.onWillInteract)
        onDidInteract.addLast(other.onDidInteract)
        onWillStopTab.addLast(other.onWillStopTab)
        onTabStopped.addLast(other.onTabStopped)

        return this
    }
}

class WebPageWebDriverHandlerPipeline: AbstractWebPageWebDriverHandler() {
    private val registeredHandlers = mutableListOf<WebPageWebDriverHandler>()

    fun addFirst(handler: suspend (WebPage, WebDriver) -> Any?): WebPageWebDriverHandlerPipeline {
        registeredHandlers.add(0, object: AbstractWebPageWebDriverHandler() {
            override suspend fun invokeDeferred(page: WebPage, driver: WebDriver): Any? {
                return handler(page, driver)
            }
        })
        return this
    }

    fun addFirst(handler: WebPageWebDriverHandler): WebPageWebDriverHandlerPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addFirst(vararg handlers: WebPageWebDriverHandler): WebPageWebDriverHandlerPipeline {
        handlers.forEach { addFirst(it) }
        return this
    }

    fun addLast(handler: suspend (WebPage, WebDriver) -> Any?): WebPageWebDriverHandlerPipeline {
        registeredHandlers.add(object: AbstractWebPageWebDriverHandler() {
            override suspend fun invokeDeferred(page: WebPage, driver: WebDriver): Any? {
                return handler(page, driver)
            }
        })
        return this
    }

    fun addLast(handler: WebPageWebDriverHandler): WebPageWebDriverHandlerPipeline {
        registeredHandlers.add(handler)
        return this
    }

    fun addLast(vararg handlers: WebPageWebDriverHandler): WebPageWebDriverHandlerPipeline {
        handlers.toCollection(registeredHandlers)
        return this
    }

    override suspend fun invokeDeferred(page: WebPage, driver: WebDriver) {
        registeredHandlers.forEach { it.invokeDeferred(page, driver) }
    }
}

abstract class AbstractWebDriverFetchResultHandler: WebDriverFetchResultHandler() {
    private val logger = getLogger(AbstractWebDriverFetchResultHandler::class)

    override fun invoke(page: WebPage, driver: WebDriver): FetchResult? {
        return runBlocking { invokeDeferred(page, driver) }
    }
}

class WebDriverFetchResultHandlerPipeline: AbstractWebDriverFetchResultHandler() {
    private val registeredHandlers = mutableListOf<WebDriverFetchResultHandler>()

    fun addFirst(handler: suspend (WebPage, WebDriver) -> FetchResult?): WebDriverFetchResultHandlerPipeline {
        registeredHandlers.add(0, object: AbstractWebDriverFetchResultHandler() {
            override suspend fun invokeDeferred(page: WebPage, driver: WebDriver): FetchResult? {
                return handler(page, driver)
            }
        })
        return this
    }

    fun addFirst(handler: WebDriverFetchResultHandler): WebDriverFetchResultHandlerPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addFirst(vararg handlers: WebDriverFetchResultHandler): WebDriverFetchResultHandlerPipeline {
        handlers.forEach { addFirst(it) }
        return this
    }

    fun addLast(handler: suspend (WebPage, WebDriver) -> FetchResult?): WebDriverFetchResultHandlerPipeline {
        registeredHandlers.add(object: AbstractWebDriverFetchResultHandler() {
            override suspend fun invokeDeferred(page: WebPage, driver: WebDriver): FetchResult? {
                return handler(page, driver)
            }
        })
        return this
    }

    fun addLast(handler: WebDriverFetchResultHandler): WebDriverFetchResultHandlerPipeline {
        registeredHandlers.add(handler)
        return this
    }

    fun addLast(vararg handlers: WebDriverFetchResultHandler): WebDriverFetchResultHandlerPipeline {
        handlers.toCollection(registeredHandlers)
        return this
    }

    override suspend fun invokeDeferred(page: WebPage, driver: WebDriver): FetchResult? {
        var result: FetchResult? = null
        registeredHandlers.forEach { result = it.invokeDeferred(page, driver) }
        return result
    }
}

class ExpressionSimulateEventHandler(
    val beforeComputeExpressions: Iterable<String> = listOf(),
    val afterComputeExpressions: Iterable<String> = listOf()
): AbstractSimulateEventHandler() {
    constructor(bcExpressions: String, acExpressions2: String, delimiters: String = ";"): this(
        bcExpressions.split(delimiters), acExpressions2.split(delimiters))

    init {
        onWillComputeFeature.addFirst(object: AbstractWebPageWebDriverHandler() {
            override suspend fun invokeDeferred(page: WebPage, driver: WebDriver): Any? {
                return evaluate(driver, beforeComputeExpressions)
            }
        })

        onFeatureComputed.addFirst(object: AbstractWebPageWebDriverHandler() {
            override suspend fun invokeDeferred(page: WebPage, driver: WebDriver): Any? {
                return evaluate(driver, afterComputeExpressions)
            }
        })
    }
}

class DefaultSimulateEventHandler: AbstractSimulateEventHandler()

interface CrawlEventHandler {
    val onFilter: UrlAwareFilterPipeline
    val onNormalize: UrlAwareFilterPipeline

    val onWillLoad: UrlAwareHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onWillLoad"))
    val onBeforeLoad: UrlAwareHandlerPipeline get() = onWillLoad

    val onLoad: UrlAwareHandlerPipeline

    val onLoaded: UrlAwareWebPageHandlerPipeline
    @Deprecated("Old fashioned name", ReplaceWith("onLoaded"))
    val onAfterLoad: UrlAwareWebPageHandlerPipeline get() = onLoaded

    fun combine(other: CrawlEventHandler): CrawlEventHandler
}

abstract class AbstractCrawlEventHandler(
    override val onFilter: UrlAwareFilterPipeline = UrlAwareFilterPipeline(),
    override val onNormalize: UrlAwareFilterPipeline = UrlAwareFilterPipeline(),
    override val onWillLoad: UrlAwareHandlerPipeline = UrlAwareHandlerPipeline(),
    override val onLoad: UrlAwareHandlerPipeline = UrlAwareHandlerPipeline(),
    override val onLoaded: UrlAwareWebPageHandlerPipeline = UrlAwareWebPageHandlerPipeline()
): CrawlEventHandler {
    override fun combine(other: CrawlEventHandler): CrawlEventHandler {
        onFilter.addLast(other.onFilter)
        onNormalize.addLast(other.onNormalize)
        onWillLoad.addLast(other.onWillLoad)
        onLoad.addLast(other.onLoad)
        onLoaded.addLast(other.onLoaded)
        return this
    }
}

class DefaultCrawlEventHandler: AbstractCrawlEventHandler()

abstract class PageDatumHandler: (String, PageDatum) -> Unit, AbstractEventHandler() {
    abstract override operator fun invoke(pageSource: String, pageDatum: PageDatum)
}

class PageDatumHandlerPipeline: PageDatumHandler(), EventHandlerPipeline {
    private val registeredHandlers = Collections.synchronizedList(mutableListOf<(String, PageDatum) -> Unit>())

    override val size: Int
        get() = registeredHandlers.size

    fun addFirst(handler: (String, PageDatum) -> Unit): PageDatumHandlerPipeline {
        registeredHandlers.add(0, handler)
        return this
    }

    fun addFirst(vararg handlers: (String, PageDatum) -> Unit): PageDatumHandlerPipeline {
        handlers.forEach { addFirst(it) }
        return this
    }

    fun addLast(handler: (String, PageDatum) -> Unit): PageDatumHandlerPipeline {
        registeredHandlers.add(handler)
        return this
    }

    fun addLast(vararg handlers: (String, PageDatum) -> Unit): PageDatumHandlerPipeline {
        handlers.toCollection(registeredHandlers)
        return this
    }

    override operator fun invoke(pageSource: String, pageDatum: PageDatum) {
        registeredHandlers.forEach { it(pageSource, pageDatum) }
    }
}

interface EmulateEventHandler {
    val onSniffPageCategory: PageDatumHandlerPipeline
    val onCheckHtmlIntegrity: PageDatumHandlerPipeline

    fun combine(other: EmulateEventHandler): EmulateEventHandler
}

abstract class AbstractEmulateEventHandler(
    override val onSniffPageCategory: PageDatumHandlerPipeline = PageDatumHandlerPipeline(),
    override val onCheckHtmlIntegrity: PageDatumHandlerPipeline = PageDatumHandlerPipeline(),
): EmulateEventHandler {
    override fun combine(other: EmulateEventHandler): EmulateEventHandler {
        onSniffPageCategory.addLast(other.onSniffPageCategory)
        onCheckHtmlIntegrity.addLast(other.onCheckHtmlIntegrity)
        return this
    }
}

class DefaultEmulateEventHandler: AbstractEmulateEventHandler() {
    override val onSniffPageCategory: PageDatumHandlerPipeline = PageDatumHandlerPipeline()
    override val onCheckHtmlIntegrity: PageDatumHandlerPipeline = PageDatumHandlerPipeline()
}

interface PulsarEventHandler {
    val loadEventHandler: LoadEventHandler
    val simulateEventHandler: SimulateEventHandler
    val crawlEventHandler: CrawlEventHandler

    fun combine(other: PulsarEventHandler): PulsarEventHandler
}

abstract class AbstractPulsarEventHandler(
    override val loadEventHandler: AbstractLoadEventHandler,
    override val simulateEventHandler: AbstractSimulateEventHandler,
    override val crawlEventHandler: AbstractCrawlEventHandler
): PulsarEventHandler {
    override fun combine(other: PulsarEventHandler): PulsarEventHandler {
        loadEventHandler.combine(other.loadEventHandler)
        simulateEventHandler.combine(other.simulateEventHandler)
        crawlEventHandler.combine(other.crawlEventHandler)
        return this
    }
}

open class DefaultPulsarEventHandler(
    loadEventHandler: DefaultLoadEventHandler = DefaultLoadEventHandler(),
    simulateEventHandler: DefaultSimulateEventHandler = DefaultSimulateEventHandler(),
    crawlEventHandler: DefaultCrawlEventHandler = DefaultCrawlEventHandler()
): AbstractPulsarEventHandler(loadEventHandler, simulateEventHandler, crawlEventHandler) {

}

open class PulsarEventHandlerTemplate(
    loadEventHandler: DefaultLoadEventHandler = DefaultLoadEventHandler(),
    simulateEventHandler: DefaultSimulateEventHandler = DefaultSimulateEventHandler(),
    crawlEventHandler: DefaultCrawlEventHandler = DefaultCrawlEventHandler()
): AbstractPulsarEventHandler(loadEventHandler, simulateEventHandler, crawlEventHandler) {
    init {
        loadEventHandler.apply {
            onFilter.addLast { url ->
                url
            }
            onNormalize.addLast { url ->
                url
            }
            onWillLoad.addLast { url ->

            }
            onWillFetch.addLast { page ->

            }
            onWillLaunchBrowser.addLast { page ->

            }
            onBrowserLaunched.addLast { page, driver ->

            }
            onFetched.addLast { page ->

            }
            onWillParse.addLast { page ->

            }
            onWillParseHTMLDocument.addLast { page ->

            }
            onWillExtract.addLast { page ->

            }
            onExtracted.addLast { page: WebPage, document: FeaturedDocument ->

            }
            onHTMLDocumentParsed.addLast { page: WebPage, document: FeaturedDocument ->

            }
            onParsed.addLast { page ->

            }
            onLoaded.addLast { page ->

            }
        }

        simulateEventHandler.apply {
            onWillCheckDOMState.addLast()
            onDOMStateChecked.addLast()
            onWillComputeFeature.addLast()
            onFeatureComputed.addLast()
        }

        crawlEventHandler.apply {
            onFilter.addLast { url: UrlAware ->
                url
            }
            onNormalize.addLast { url: UrlAware ->
                url
            }
            onWillLoad.addLast { url: UrlAware ->

            }
            onLoaded.addLast { url, page ->

            }
        }
    }
}
