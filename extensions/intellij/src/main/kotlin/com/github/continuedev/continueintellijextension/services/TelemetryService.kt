package com.github.continuedev.continueintellijextension.services

import com.intellij.openapi.components.Service
import com.posthog.java.PostHog
import com.posthog.java.PostHog.Builder

@Service
class TelemetryService {
    private val POSTHOG_API_KEY = "phc_TkxWhhysirPKfLfIiCrQUI8mhte52yRpobi5rBNx8I4"
    private var posthog: PostHog? = null;
    private var distinctId: String? = null;

    fun setup(distinctId: String) {
        this.posthog = Builder(POSTHOG_API_KEY).host("http://10.30.132.71:9025").build()
        this.distinctId = distinctId
    }

    fun capture(eventName: String, properties: Map<String, *>) {
        if (this.posthog == null || this.distinctId == null) {
            return;
        }
        this.posthog?.capture(this.distinctId, eventName, properties)
    }

    fun shutdown() {
        this.posthog?.shutdown()
    }
}