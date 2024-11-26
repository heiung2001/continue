package com.github.continuedev.continueintellijextension.`continue`

import com.github.continuedev.continueintellijextension.services.ContinuePluginService
import com.github.continuedev.continueintellijextension.services.TelemetryService
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import java.nio.file.Paths
import kotlinx.coroutines.*

class CoreMessengerManager(
    private val project: Project,
    private val ideProtocolClient: IdeProtocolClient,
    private val coroutineScope: CoroutineScope
) {

  var coreMessenger: CoreMessenger? = null
  var lastBackoffInterval = 0.5

  init {
    coroutineScope.launch {
      val continuePluginService =
          ServiceManager.getService(project, ContinuePluginService::class.java)

      val myPluginId = "com.github.continuedev.continueintellijextension"
      val pluginDescriptor =
          PluginManager.getPlugin(PluginId.getId(myPluginId)) ?: throw Exception("Plugin not found")

      val pluginPath = pluginDescriptor.pluginPath
      val osName = System.getProperty("os.name").toLowerCase()
      val os =
          when {
            osName.contains("mac") || osName.contains("darwin") -> "darwin"
            osName.contains("win") -> "win32"
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> "linux"
            else -> "linux"
          }
      val osArch = System.getProperty("os.arch").toLowerCase()
      val arch =
          when {
            osArch.contains("aarch64") || (osArch.contains("arm") && osArch.contains("64")) ->
                "arm64"
            osArch.contains("amd64") || osArch.contains("x86_64") -> "x64"
            else -> "x64"
          }
      val target = "$os-$arch"

      println("Identified OS: $os, Arch: $arch")

      val corePath = Paths.get(pluginPath.toString(), "core").toString()
      val targetPath = Paths.get(corePath, target).toString()
      val continueCorePath =
          Paths.get(targetPath, "continue-binary" + (if (os == "win32") ".exe" else "")).toString()

      setupCoreMessenger(continueCorePath)
    }
  }

  private fun setupCoreMessenger(continueCorePath: String): Unit {
    coreMessenger = CoreMessenger(project, continueCorePath, ideProtocolClient, coroutineScope)

    coreMessenger?.request("config/getSerializedProfileInfo", null, null) { resp ->
      val data = resp as? Map<String, Any>
      val profileInfo = data?.get("config") as? Map<String, Any>
      val allowAnonymousTelemetry = profileInfo?.get("allowAnonymousTelemetry") as? Boolean
      val telemetryService = service<TelemetryService>()
      if (allowAnonymousTelemetry == true || allowAnonymousTelemetry == null) {
        telemetryService.setup(getMachineUniqueID(), ideProtocolClient)
      }
    }

    val pluginId = "com.github.continuedev.continueintellijextension"
    val plugin = PluginManagerCore.getPlugin(PluginId.getId(pluginId))
    val extensionVersion = plugin?.version ?: "Unknown"

    val osName = System.getProperty("os.name").toLowerCase()
    val os =
      when {
        osName.contains("mac") || osName.contains("darwin") -> "darwin"
        osName.contains("win") -> "win32"
        osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> "linux"
        else -> "linux"
      }

    coreMessenger?.request(
      "version/getLatest",
      mapOf(
        "os" to os,
        "ide" to "jetbrains",
        "version" to extensionVersion,
      ),
      null,
      ({ response ->
        val isLatest = (response as? Map<*, *>)?.get("isLatest") as Boolean
        val latestVersion = (response as? Map<*, *>)?.get("latestVersion") as String
        val downloadLink = (response as? Map<*, *>)?.get("downloadLink") as String

        if (!isLatest) {
          val notification = Notification(
            "Continue",
            "CoDev update available.",
            "Your current version is $extensionVersion but the latest version is $latestVersion",
            NotificationType.INFORMATION
          )

          // Download action
          notification.addAction(object : NotificationAction("Download") {
            override fun actionPerformed(p0: AnActionEvent, p1: Notification) {
              BrowserUtil.browse(downloadLink)
              notification.expire()
            }
          })

          // Ignore action
          notification.addAction(object : NotificationAction("Ignore") {
            override fun actionPerformed(p0: AnActionEvent, p1: Notification) {
              notification.expire()
            }
          })

          notification.notify(project)
        }
      })
    )

    // On exit, use exponential backoff to create another CoreMessenger
    coreMessenger?.onDidExit {
      lastBackoffInterval *= 2
      println("CoreMessenger exited, retrying in $lastBackoffInterval seconds")
      Thread.sleep((lastBackoffInterval * 1000).toLong())
      setupCoreMessenger(continueCorePath)
    }
  }
}
