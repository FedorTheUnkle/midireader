package com.fedorkzsoft.midireader.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.event.ActionListener
import javax.sound.midi.*
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class MidiListenerConfigurable : Configurable {
    
    private var settingsPanel: JPanel? = null
    private val triggerNoteField = JBTextField()
    private val velocityThresholdField = JBTextField()
    private val enableDebuggerControlCheckbox = JBCheckBox("Enable MIDI Debugger Control")
    private val learnButton = JButton("Learn")
    
    private val noteHelpLabel = JBLabel("<html><small>MIDI note number (0-127). Common notes: C4=60, D4=62, E4=64, F4=65, G4=67, A4=69</small></html>")
    private val velocityHelpLabel = JBLabel("<html><small>Minimum velocity (1-127) required to trigger the action</small></html>")
    private val learnStatusLabel = JBLabel("")
    
    private var isLearning = false
    private var midiDevices = mutableListOf<MidiDevice>()
    
    override fun getDisplayName(): String = "MIDI Listener"
    
    override fun createComponent(): JComponent {
        val settings = MidiListenerSettings.getInstance()
        
        triggerNoteField.text = settings.triggerNote.toString()
        velocityThresholdField.text = settings.triggerVelocityThreshold.toString()
        enableDebuggerControlCheckbox.isSelected = settings.enableDebuggerControl
        
        // Setup Learn button
        learnButton.addActionListener {
            if (!isLearning) {
                startLearning()
            } else {
                stopLearning()
            }
        }
        
        // Create panel with Learn button next to trigger note field
        val triggerNotePanel = JPanel().apply {
            layout = java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0)
            add(triggerNoteField.apply { 
                preferredSize = java.awt.Dimension(100, preferredSize.height) 
            })
            add(learnButton)
        }
        
        settingsPanel = FormBuilder.createFormBuilder()
            .addComponent(enableDebuggerControlCheckbox)
            .addVerticalGap(10)
            .addLabeledComponent(JBLabel("Trigger MIDI Note:"), triggerNotePanel)
            .addComponentToRightColumn(noteHelpLabel)
            .addComponentToRightColumn(learnStatusLabel)
            .addVerticalGap(5)
            .addLabeledComponent(JBLabel("Minimum Velocity:"), velocityThresholdField)
            .addComponentToRightColumn(velocityHelpLabel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        
        return settingsPanel!!
    }
    
    override fun isModified(): Boolean {
        val settings = MidiListenerSettings.getInstance()
        return triggerNoteField.text.toIntOrNull() != settings.triggerNote ||
               velocityThresholdField.text.toIntOrNull() != settings.triggerVelocityThreshold ||
               enableDebuggerControlCheckbox.isSelected != settings.enableDebuggerControl
    }
    
    override fun apply() {
        val settings = MidiListenerSettings.getInstance()
        
        // Validate and save trigger note
        val note = triggerNoteField.text.toIntOrNull()
        if (note != null && note in 0..127) {
            settings.triggerNote = note
        } else {
            triggerNoteField.text = settings.triggerNote.toString()
        }
        
        // Validate and save velocity threshold
        val velocity = velocityThresholdField.text.toIntOrNull()
        if (velocity != null && velocity in 1..127) {
            settings.triggerVelocityThreshold = velocity
        } else {
            velocityThresholdField.text = settings.triggerVelocityThreshold.toString()
        }
        
        settings.enableDebuggerControl = enableDebuggerControlCheckbox.isSelected
    }
    
    override fun reset() {
        val settings = MidiListenerSettings.getInstance()
        triggerNoteField.text = settings.triggerNote.toString()
        velocityThresholdField.text = settings.triggerVelocityThreshold.toString()
        enableDebuggerControlCheckbox.isSelected = settings.enableDebuggerControl
        
        // Stop learning if active
        if (isLearning) {
            stopLearning()
        }
    }
    
    override fun disposeUIResources() {
        super.disposeUIResources()
        // Clean up MIDI devices when settings dialog is closed
        if (isLearning) {
            stopLearning()
        }
    }
    
    private fun startLearning() {
        isLearning = true
        learnButton.text = "Stop Learning"
        learnStatusLabel.text = "<html><font color='blue'>🎹 Waiting for MIDI input... Play a note!</font></html>"
        
        Thread {
            try {
                val infos = MidiSystem.getMidiDeviceInfo()
                
                infos.forEach { info ->
                    try {
                        val device = MidiSystem.getMidiDevice(info)
                        
                        // Check if device can transmit (is an input device)
                        if (device.maxTransmitters != 0) {
                            if (!device.isOpen) {
                                device.open()
                            }
                            
                            val transmitter = device.transmitter
                            transmitter.receiver = object : Receiver {
                                override fun send(message: MidiMessage, timeStamp: Long) {
                                    if (!isLearning) return
                                    
                                    if (message is ShortMessage) {
                                        if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) {
                                            val noteNumber = message.data1
                                            val noteName = getNoteName(noteNumber)
                                            
                                            // Update UI on EDT
                                            SwingUtilities.invokeLater {
                                                triggerNoteField.text = noteNumber.toString()
                                                learnStatusLabel.text = "<html><font color='green'>✅ Learned: $noteName (MIDI $noteNumber)</font></html>"
                                                stopLearning()
                                            }
                                        }
                                    }
                                }
                                
                                override fun close() {}
                            }
                            
                            midiDevices.add(device)
                        }
                    } catch (e: MidiUnavailableException) {
                        // Skip devices we can't open
                    }
                }
                
                if (midiDevices.isEmpty()) {
                    SwingUtilities.invokeLater {
                        learnStatusLabel.text = "<html><font color='red'>❌ No MIDI devices found!</font></html>"
                        stopLearning()
                    }
                }
                
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    learnStatusLabel.text = "<html><font color='red'>❌ Error: ${e.message}</font></html>"
                    stopLearning()
                }
            }
        }.start()
    }
    
    private fun stopLearning() {
        isLearning = false
        learnButton.text = "Learn"
        
        // Close all MIDI devices
        midiDevices.forEach { device ->
            try {
                if (device.isOpen) {

                    val transmitters = device.transmitters
                    transmitters.forEach { transmitter ->
                        transmitter.close()
                    }

                    device.close()
                }
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
        midiDevices.clear()
    }
    
    private fun getNoteName(midiNote: Int): String {
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val octave = (midiNote / 12) - 1
        val note = midiNote % 12
        return "${noteNames[note]}$octave"
    }
}