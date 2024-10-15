package com.github.continuedev.continueintellijextension.services

import com.github.continuedev.continueintellijextension.constants.getConfigJsonPath
import com.intellij.openapi.components.Service
import com.posthog.java.PostHog
import com.posthog.java.PostHog.Builder
import com.github.continuedev.continueintellijextension.`continue`.IdeProtocolClient
import com.google.gson.GsonBuilder
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import java.io.FileReader
import java.util.ArrayList

private fun readConfigJson(): Map<String, Any> {
    val gson = GsonBuilder().setPrettyPrinting().create()
    val configJsonPath = getConfigJsonPath()
    val reader = FileReader(configJsonPath)
    val config: Map<String, Any> = gson.fromJson(
            reader,
            object : TypeToken<Map<String, Any>>() {}.type
    )
    reader.close()
    return config
}

@Service
class TelemetryService {
    private val POSTHOG_API_KEY = "phc_OdIcJ0UWlitsGPTLh29EaprsOnGMdEODlbz6KdUOrjm"
    private var posthog: PostHog? = null;
    private var distinctId: String? = null;
    private var ideInfo: Map<String, *>? = null;
    private var apiKeys: Map<String, *>? = null;
    
    fun setup(distinctId: String, ideProtocolClient: IdeProtocolClient) {
        this.posthog = Builder(POSTHOG_API_KEY).host("https://app.posthog.com").build()
        this.distinctId = distinctId

        val request = """{"messageType": "getIdeInfo"}"""
        ideProtocolClient.handleMessage(request) { data ->
            @Suppress("UNCHECKED_CAST")
            val ideInfo: Map<String, *> = data as Map<String, *>
            this.ideInfo = ideInfo
        }

        val config = readConfigJson()
        val chatModelApi = (((config["models"] as ArrayList<*>)[0] as LinkedTreeMap<*, *>)["apiKey"] as String).takeLast(4)
        val compModelApi = ((config["tabAutocompleteModel"] as LinkedTreeMap<*, *>)["apiKey"] as String).takeLast(4)

        this.apiKeys = mapOf(
                "chatApiKey" to chatModelApi,
                "compApiKey" to compModelApi,
        )
    }

    fun capture(eventName: String, properties: Map<String, *>) {
        if (this.posthog == null || this.distinctId == null) {
            return;
        }
        this.posthog?.capture(this.distinctId, eventName, properties + (this.ideInfo as Map<String, *>) + (this.apiKeys as Map<String, *>))
    }

    fun shutdown() {
        this.posthog?.shutdown()
    }
}