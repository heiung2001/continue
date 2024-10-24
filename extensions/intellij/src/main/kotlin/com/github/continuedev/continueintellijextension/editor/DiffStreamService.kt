package com.github.continuedev.continueintellijextension.editor

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor

import com.intellij.openapi.components.service
import com.github.continuedev.continueintellijextension.services.TelemetryService

@Service(Service.Level.PROJECT)
class DiffStreamService {
    private val handlers = mutableMapOf<Editor, DiffStreamHandler>()
    private val telemetryService = service<TelemetryService>()

    fun register(handler: DiffStreamHandler, editor: Editor) {
        if (handlers.containsKey(editor)) {
            handlers[editor]?.rejectAll()
        }
        handlers[editor] = handler
        println("Registered handler for editor")
    }

    fun reject(editor: Editor) {
        this.telemetryService.capture(
                "rejectDiff",
                mapOf(
                        "linesAdded" to handlers[editor]?.getLinesAdded(),
                        "linesRemoved" to handlers[editor]?.getLinesRemoved(),
                )
        )
        handlers[editor]?.rejectAll()
        handlers.remove(editor)
    }

    fun accept(editor: Editor) {
        this.telemetryService.capture(
                "acceptDiff",
                mapOf(
                        "linesAdded" to handlers[editor]?.getLinesAdded(),
                        "linesRemoved" to handlers[editor]?.getLinesRemoved()
                )
        )
        handlers[editor]?.acceptAll()
        handlers.remove(editor)
    }
}