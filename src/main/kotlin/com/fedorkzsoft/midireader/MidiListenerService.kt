package com.fedorkzsoft.midireader

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.xdebugger.XDebugSession
import javax.sound.midi.*

@Service(Service.Level.PROJECT)
class MidiListenerService(private val project: Project) {
    
    private val logger = Logger.getInstance(MidiListenerService::class.java)
    private val openDevices = mutableListOf<MidiDevice>()
    var isListening = false
        private set

    fun startListening() {
        if (isListening) {
            logger.info("MIDI Listener is already running")
            return
        }

        logger.info("Starting MIDI Listener...")
        
        try {
            val infos = MidiSystem.getMidiDeviceInfo()
            logger.info("Found ${infos.size} MIDI devices")

            infos.forEach { info ->
                try {
                    val device = MidiSystem.getMidiDevice(info)
                    
                    // Check if device can transmit (is an input device)
                    if (device.maxTransmitters != 0) {
                        logger.info("Opening MIDI device: ${info.name} (${info.description})")
                        
                        if (!device.isOpen) {
                            device.open()
                        }
                        
                        val transmitter = device.transmitter
                        transmitter.receiver = MidiReceiver()
                        
                        openDevices.add(device)
                        logger.info("Successfully opened device: ${info.name}")
                    }
                } catch (e: MidiUnavailableException) {
                    logger.warn("Could not open MIDI device: ${info.name}", e)
                }
            }
            
            isListening = true
            logger.info("MIDI Listener started successfully. Listening on ${openDevices.size} device(s)")
            
        } catch (e: Exception) {
            logger.error("Error starting MIDI Listener", e)
        }
    }

    fun stopListening() {
        if (!isListening) {
            logger.info("MIDI Listener is not running")
            return
        }

        logger.info("Stopping MIDI Listener...")
        
        openDevices.forEach { device ->
            if (device.isOpen) {
                device.close()
                logger.info("Closed MIDI device: ${device.deviceInfo.name}")
            }
        }
        
        openDevices.clear()
        isListening = false
        logger.info("MIDI Listener stopped")
    }

    private inner class MidiReceiver : Receiver {
        
        override fun send(message: MidiMessage, timeStamp: Long) {
            val logMessage = buildString {
                append("MIDI Event [$timeStamp]: ")
                
                when (message) {
                    is ShortMessage -> {
                        val command = message.command
                        val channel = message.channel
                        val data1 = message.data1
                        val data2 = message.data2
                        
                        val commandName = getCommandName(command)
                        append("$commandName | Channel: $channel | Data1: $data1 | Data2: $data2")
                        
                        // Add human-readable info for common messages
                        when (command) {
                            ShortMessage.NOTE_ON -> {
                                append(" | Note: ${getNoteName(data1)} | Velocity: $data2")

                                if (data2 > 0) {
                                    consumeMidiNoteOnEvent(data1)
                                }
                            }
                            ShortMessage.NOTE_OFF -> {
                                append(" | Note: ${getNoteName(data1)}")
                            }
                            ShortMessage.CONTROL_CHANGE -> {
                                append(" | Controller: $data1")
                            }
                        }
                    }
                    is SysexMessage -> {
                        append("SysEx | Length: ${message.length}")
                    }
                    is MetaMessage -> {
                        append("Meta | Type: ${message.type}")
                    }
                    else -> {
                        append("Unknown message type: ${message::class.java.name}")
                    }
                }
            }
            
            logger.info(logMessage)
        }

        override fun close() {
            logger.info("MIDI Receiver closed")
        }
        
        private fun getCommandName(command: Int): String = when (command) {
            ShortMessage.NOTE_OFF -> "NOTE_OFF"
            ShortMessage.NOTE_ON -> "NOTE_ON"
            ShortMessage.POLY_PRESSURE -> "POLY_PRESSURE"
            ShortMessage.CONTROL_CHANGE -> "CONTROL_CHANGE"
            ShortMessage.PROGRAM_CHANGE -> "PROGRAM_CHANGE"
            ShortMessage.CHANNEL_PRESSURE -> "CHANNEL_PRESSURE"
            ShortMessage.PITCH_BEND -> "PITCH_BEND"
            else -> "UNKNOWN($command)"
        }
        
        private fun getNoteName(midiNote: Int): String {
            val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            val octave = (midiNote / 12) - 1
            val note = midiNote % 12
            return "${noteNames[note]}$octave"
        }
    }

    private fun consumeMidiNoteOnEvent(data1: Int) {
        when (data1) {
            73 -> triggerDebugAction {
                it.stepOver(false)
            }
            75 -> triggerDebugAction {
                it.stepInto()
            }
            74 -> triggerDebugAction {
                it.stepOut()
            }
            72 -> triggerDebugAction {
                it.resume()
            }
        }
    }
    private fun triggerDebugAction(action: (XDebugSession) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val debuggerManager = XDebuggerManager.getInstance(project)
                val currentSession = debuggerManager.currentSession

                if (currentSession != null && currentSession.isPaused) {
                    logger.info("✅ Debug session found and paused. Executing step over...")
                    action(currentSession)
                    logger.info("✅ Step over triggered successfully!")
                } else if (currentSession != null && !currentSession.isPaused) {
                    logger.warn("⚠️ Debug session is running but not paused. Cannot step over.")
                    logger.info("💡 Set a breakpoint and pause execution first, then play C4")
                } else {
                    logger.warn("⚠️ No active debug session found.")
                    logger.info("💡 Start debugging first (Debug button 🐞), set a breakpoint, then play C4")
                }
            } catch (e: Exception) {
                logger.error("❌ Error triggering debugger step over", e)
            }
        }
    }
}
