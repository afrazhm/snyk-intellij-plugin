package io.snyk.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import ly.iterative.itly.Environment
import ly.iterative.itly.IterativelyOptions
import ly.iterative.itly.Options
import ly.iterative.itly.ValidationOptions
import ly.iterative.itly.segment.SegmentPlugin
import snyk.analytics.AnalysisIsReady
import snyk.analytics.AnalysisIsTriggered
import snyk.analytics.DestinationsOptions
import snyk.analytics.Identify
import snyk.analytics.IssueIsViewed
import snyk.analytics.Itly
import snyk.analytics.ItlyLogger
import snyk.analytics.ProductSelectionIsViewed
import snyk.analytics.WelcomeIsViewed
import java.util.Properties

@Service
class SnykAnalyticsService : Disposable {
    private val log = logger<SnykAnalyticsService>()

    private var analyticsCollectionEnabled = true

    private var anonymousId = ""
    private var userId = ""

    init {
        val settings = service<SnykApplicationSettingsStateService>()
        analyticsCollectionEnabled = settings.usageAnalyticsEnabled
        anonymousId = settings.userAnonymousId
        userId = obtainUserId(settings.token)

        val environment = loadIterativelyEnvironment()
        log.warn("Initializing Iteratively integration for $environment...")
        Itly.getInstance().load(
            DestinationsOptions.builder()
                .iteratively(
                    IterativelyOptions.builder()
                        .flushIntervalMs(10_000)
                        .flushQueueSize(5)
                        .build()
                )
                .build(),
            Options.builder()
                .environment(environment)
                .validation(
                    ValidationOptions.builder()
                        .errorOnInvalid(true)
                        .trackInvalid(false)
                        .build()
                )
                .logger(ItlyLogger(log))
                .plugins(listOf(SegmentPlugin(loadSegmentWriteKey())))
                .build()
        )
    }

    override fun dispose() {
        Itly.getInstance().shutdown()
    }

    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        log.info("Analytics collection is activated: '$enabled'")

        analyticsCollectionEnabled = enabled
    }

    fun setUserId(userId: String) {
        this.userId = userId
    }

    fun obtainUserId(token: String?): String {
        if (token.isNullOrBlank()) {
            log.warn("Token is null or empty, user public id will not be obtained.")
            return ""
        }
        val userId = service<SnykApiService>().userId
        if (userId == null) {
            log.warn("Not able to obtain user public id.")
            return ""
        }
        return userId
    }

    fun identify() {
        catchAll(log, "identify") {
            Itly.getInstance().identify(anonymousId, Identify.builder().build())
        }
    }

    fun alias(userId: String) {
        catchAll(log, "alias") {
            Itly.getInstance().alias(userId, anonymousId)
        }
    }

    fun logWelcomeIsViewed(event: WelcomeIsViewed) {
        if (!analyticsCollectionEnabled) {
            return
        }

        val userId = this.userId.ifEmpty { this.anonymousId }
        catchAll(log, "welcomeIsViewed") {
            Itly.getInstance().welcomeIsViewed(userId, event)
        }
    }

    fun logProductSelectionIsViewed(event: ProductSelectionIsViewed) {
        if (!analyticsCollectionEnabled || userId.isBlank()) {
            return
        }

        catchAll(log, "productSelectionIsViewed") {
            Itly.getInstance().productSelectionIsViewed(userId, event)
        }
    }

    fun logAnalysisIsTriggered(event: AnalysisIsTriggered) {
        if (!analyticsCollectionEnabled || userId.isBlank()) {
            return
        }

        catchAll(log, "analysisIsTriggered") {
            Itly.getInstance().analysisIsTriggered(userId, event)
        }
    }

    fun logAnalysisIsReady(event: AnalysisIsReady) {
        if (!analyticsCollectionEnabled || userId.isBlank()) {
            return
        }

        catchAll(log, "analysisIsReady") {
            Itly.getInstance().analysisIsReady(userId, event)
        }
    }

    fun logIssueIsViewed(event: IssueIsViewed) {
        if (!analyticsCollectionEnabled || userId.isBlank()) {
            return
        }

        catchAll(log, "issueIsViewed") {
            Itly.getInstance().issueIsViewed(userId, event)
        }
    }

    private inline fun catchAll(log: Logger, message: String, action: () -> Unit) {
        try {
            action()
        } catch (t: Throwable) {
            log.warn("Failed to execute '$message' analytic event. ${t.message}", t)
        }
    }

    private fun loadIterativelyEnvironment(): Environment {
        return try {
            val prop = Properties()
            prop.load(javaClass.classLoader.getResourceAsStream("/application.properties"))
            val environment = prop.getProperty("iteratively.analytics.environment") ?: "DEVELOPMENT"

            if (environment.isEmpty()) {
                log.warn("Iteratively environment is empty. Use DEVELOPMENT as default")
                Environment.DEVELOPMENT
            } else {
                Environment.valueOf(environment)
            }
        } catch (t: Throwable) {
            log.warn("Could not load application.properties. Use DEVELOPMENT environment.", t)
            Environment.DEVELOPMENT
        }
    }

    private fun loadSegmentWriteKey(): String {
        return try {
            val prop = Properties()
            prop.load(javaClass.classLoader.getResourceAsStream("/application.properties"))
            val writeKey = prop.getProperty("segment.analytics.write-key") ?: ""

            writeKey.ifEmpty {
                analyticsCollectionEnabled = false
                log.warn("Segment analytics collection is disabled because write key is empty!")
                ""
            }
        } catch (t: Throwable) {
            log.warn("Failed to create Segment analytics. Event collection is disabled.", t)
            ""
        }
    }
}
