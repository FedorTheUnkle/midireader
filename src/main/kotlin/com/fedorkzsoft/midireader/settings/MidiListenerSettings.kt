package com.fedorkzsoft.midireader.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.Service
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "MidiListenerSettings",
    storages = [Storage("MidiListenerPlugin.xml")]
)
@Service(Service.Level.APP)
class MidiListenerSettings : PersistentStateComponent<MidiListenerSettings> {
    
    var triggerNote: Int = 60  // C4 by default
    var triggerVelocityThreshold: Int = 1  // Minimum velocity to trigger
    var enableDebuggerControl: Boolean = true  // Enable/disable the feature
    
    override fun getState(): MidiListenerSettings = this
    
    override fun loadState(state: MidiListenerSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
    
    companion object {
        fun getInstance(): MidiListenerSettings {
            return com.intellij.openapi.application.ApplicationManager
                .getApplication()
                .getService(MidiListenerSettings::class.java)
        }
    }
}
