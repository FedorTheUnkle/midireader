package com.fedorkzsoft.midireader

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class StopMidiListenerAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { project ->
            project.getService(MidiListenerService::class.java).stopListening()
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.project?.let { project ->
            val service = project.getService(MidiListenerService::class.java)
            e.presentation.isEnabled = service.isListening
        } ?: run {
            e.presentation.isEnabled = false
        }
    }
}
