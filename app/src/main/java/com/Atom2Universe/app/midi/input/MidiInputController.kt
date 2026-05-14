package com.Atom2Universe.app.midi.input

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

enum class MidiConnectionStatus {
    NO_DEVICE,
    DEVICE_DETECTED,
    DEVICE_UNAUTHORIZED
}

/**
 * Information about a connected MIDI keyboard.
 */
data class MidiKeyboardInfo(
    val name: String,
    val manufacturer: String,
    val product: String,
    val type: Int,  // MidiDeviceInfo.TYPE_USB, TYPE_BLUETOOTH, TYPE_VIRTUAL
    val inputPortCount: Int,
    val outputPortCount: Int
)

class MidiInputController(
    context: Context,
    private val onMidiBytesReceived: (ByteArray) -> Unit,
    private val onStatusChanged: ((MidiConnectionStatus) -> Unit)? = null
) {
    private val appContext = context.applicationContext
    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val handler = Handler(Looper.getMainLooper())

    private var midiDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null
    private var outputPort: MidiOutputPort? = null
    private var pendingDeviceInfo: MidiDeviceInfo? = null
    private var pendingUsbDevice: UsbDevice? = null
    private var connectedDeviceInfo: MidiDeviceInfo? = null
    private var isReceiverRegistered = false
    private var isDeviceCallbackRegistered = false
    private var isActive = false
    private val midiReceiver = object : MidiReceiver() {
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            handleMidiData(data, offset, count)
        }
    }

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            if (isActive && midiDevice == null) {
                handler.post { tryConnectDevice(device) }
            }
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            handler.post {
                closeCurrentDevice()
                onStatusChanged?.invoke(MidiConnectionStatus.NO_DEVICE)
            }
        }
    }
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (device == null) {
                return
            }
            if (device != pendingUsbDevice) {
                return
            }
            if (granted && pendingDeviceInfo != null) {
                val deviceInfo = pendingDeviceInfo
                pendingDeviceInfo = null
                pendingUsbDevice = null
                if (deviceInfo != null) {
                    openMidiDevice(deviceInfo)
                }
            } else {
                onStatusChanged?.invoke(MidiConnectionStatus.DEVICE_UNAUTHORIZED)
            }
        }
    }

    fun start() {
        stop()
        isActive = true
        registerDeviceCallback()

        @Suppress("DEPRECATION")
        val devices = midiManager.devices

        if (devices.isNullOrEmpty()) {
            onStatusChanged?.invoke(MidiConnectionStatus.NO_DEVICE)
            return
        }
        val deviceInfo = devices.firstOrNull()
        if (deviceInfo == null) {
            onStatusChanged?.invoke(MidiConnectionStatus.NO_DEVICE)
            return
        }
        tryConnectDevice(deviceInfo)
    }

    fun stop() {
        isActive = false
        closeCurrentDevice()
        unregisterDeviceCallback()
        unregisterPermissionReceiver()
        onStatusChanged?.invoke(MidiConnectionStatus.NO_DEVICE)
    }

    private fun closeCurrentDevice() {
        outputPort?.close()
        outputPort = null
        inputPort?.close()
        inputPort = null
        midiDevice?.close()
        midiDevice = null
        pendingDeviceInfo = null
        pendingUsbDevice = null
        connectedDeviceInfo = null
    }

    private fun tryConnectDevice(deviceInfo: MidiDeviceInfo) {
        val usbDevice = getUsbDevice(deviceInfo)
        if (usbDevice != null && !usbManager.hasPermission(usbDevice)) {
            pendingDeviceInfo = deviceInfo
            pendingUsbDevice = usbDevice
            onStatusChanged?.invoke(MidiConnectionStatus.DEVICE_UNAUTHORIZED)
            requestUsbPermission(usbDevice)
            return
        }
        openMidiDevice(deviceInfo)
    }

    private fun registerDeviceCallback() {
        if (isDeviceCallbackRegistered) return
        @Suppress("DEPRECATION")
        midiManager.registerDeviceCallback(deviceCallback, handler)
        isDeviceCallbackRegistered = true
    }

    private fun unregisterDeviceCallback() {
        if (!isDeviceCallbackRegistered) return
        midiManager.unregisterDeviceCallback(deviceCallback)
        isDeviceCallbackRegistered = false
    }

    private fun openMidiDevice(deviceInfo: MidiDeviceInfo) {
        midiManager.openDevice(deviceInfo, { device ->
            if (device == null) {
                onStatusChanged?.invoke(MidiConnectionStatus.DEVICE_UNAUTHORIZED)
                return@openDevice
            }
            midiDevice = device
            connectedDeviceInfo = deviceInfo
            openPorts(device, deviceInfo)
            onStatusChanged?.invoke(MidiConnectionStatus.DEVICE_DETECTED)
        }, handler)
    }

    private fun openPorts(device: MidiDevice, deviceInfo: MidiDeviceInfo) {
        android.util.Log.d(TAG, "openPorts: outputPortCount=${deviceInfo.outputPortCount} inputPortCount=${deviceInfo.inputPortCount}")

        // Pour recevoir les notes du clavier, on utilise le OUTPUT port du device
        // (le clavier "envoie" via son output, on "recoit" donc via ce port)
        if (deviceInfo.outputPortCount > 0) {
            outputPort = device.openOutputPort(0)
            if (outputPort != null) {
                outputPort?.connect(midiReceiver)
                android.util.Log.d(TAG, "openPorts: outputPort opened (receive notes from keyboard)")
            }
        }

        // Input port est pour envoyer des donnees AU clavier (ex: allumer les LEDs)
        if (deviceInfo.inputPortCount > 0) {
            inputPort = device.openInputPort(0)
            android.util.Log.d(TAG, "openPorts: inputPort opened=${inputPort != null} (send to keyboard LEDs)")
        } else {
            android.util.Log.w(TAG, "openPorts: No input port available - cannot control LEDs")
        }
    }

    private fun requestUsbPermission(usbDevice: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(appContext.packageName)
        }
        val permissionIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )
        registerPermissionReceiver()
        usbManager.requestPermission(usbDevice, permissionIntent)
    }

    private fun registerPermissionReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            appContext,
            usbPermissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isReceiverRegistered = true
    }

    private fun unregisterPermissionReceiver() {
        if (!isReceiverRegistered) return
        appContext.unregisterReceiver(usbPermissionReceiver)
        isReceiverRegistered = false
    }

    private fun getUsbDevice(deviceInfo: MidiDeviceInfo): UsbDevice? {
        val properties = deviceInfo.properties
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            properties.getParcelable(MidiDeviceInfo.PROPERTY_USB_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            properties.getParcelable(MidiDeviceInfo.PROPERTY_USB_DEVICE)
        }
    }

    private fun handleMidiData(data: ByteArray, offset: Int, count: Int) {
        var index = offset
        val end = offset + count
        while (index + 2 < end) {
            val status = data[index].toInt() and 0xFF
            val messageType = status and 0xF0
            if (messageType == NOTE_OFF || messageType == NOTE_ON) {
                val note = data[index + 1]
                val velocity = data[index + 2]
                val midiBytes = if (messageType == NOTE_ON && velocity.toInt() == 0) {
                    byteArrayOf((NOTE_OFF or (status and 0x0F)).toByte(), note, velocity)
                } else {
                    byteArrayOf(data[index], note, velocity)
                }
                // BUG FIX 3.29: Poster sur le main thread pour éviter les problèmes de timing
                // Le callback MIDI peut arriver depuis n'importe quel thread
                handler.post {
                    onMidiBytesReceived(midiBytes)
                }
                index += 3
            } else {
                index += 1
            }
        }
    }

    // ========== Methodes d'envoi vers le clavier (pour LEDs) ==========

    /**
     * Envoie des donnees MIDI au clavier (via son input port)
     * Utilise pour controler les LEDs du clavier
     * @return true si envoye avec succes, false si pas de port disponible
     */
    fun sendToKeyboard(bytes: ByteArray): Boolean {
        val port = inputPort
        if (port == null) {
            return false
        }
        return try {
            port.send(bytes, 0, bytes.size)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Envoie un NOTE_ON pour allumer une LED du clavier
     */
    fun sendNoteOn(note: Int, velocity: Int = 127, channel: Int = 0) {
        val sent = sendToKeyboard(byteArrayOf(
            (NOTE_ON or channel).toByte(),
            note.toByte(),
            velocity.toByte()
        ))
        android.util.Log.d(TAG, "sendNoteOn note=$note vel=$velocity ch=$channel sent=$sent hasInputPort=${inputPort != null}")
    }

    /**
     * Envoie un NOTE_OFF pour eteindre une LED du clavier
     */
    fun sendNoteOff(note: Int, channel: Int = 0) {
        val sent = sendToKeyboard(byteArrayOf(
            (NOTE_OFF or channel).toByte(),
            note.toByte(),
            0.toByte()
        ))
        android.util.Log.d(TAG, "sendNoteOff note=$note ch=$channel sent=$sent")
    }

    /**
     * Envoie un Control Change au clavier
     * @param channel Canal MIDI (0-15)
     * @param controller Numero du controleur (0-127)
     * @param value Valeur (0-127)
     */
    fun sendControlChange(channel: Int, controller: Int, value: Int): Boolean {
        return sendToKeyboard(byteArrayOf(
            (0xB0 or (channel and 0x0F)).toByte(),  // Control Change + channel
            (controller and 0x7F).toByte(),
            (value and 0x7F).toByte()
        ))
    }

    /**
     * Eteint toutes les LEDs (envoie All Notes Off sur tous les canaux)
     */
    fun sendAllNotesOff() {
        // Control Change 123 = All Notes Off
        for (channel in 0..15) {
            sendToKeyboard(byteArrayOf(
                (0xB0 or channel).toByte(),  // Control Change
                123.toByte(),                 // All Notes Off
                0.toByte()
            ))
        }
    }

    /**
     * Desactive le synthetiseur local du clavier (Local Control Off)
     * Le clavier continuera a envoyer des notes MIDI et a recevoir les commandes LED,
     * mais ne jouera plus de son lui-meme.
     *
     * Utilise pour eviter que le clavier joue les notes du fichier MIDI
     * quand on envoie des commandes pour allumer les LEDs.
     */
    fun sendLocalControlOff() {
        android.util.Log.i(TAG, "sendLocalControlOff: sending CC122=0 to all 16 channels")
        // Control Change 122 = Local Control, valeur 0 = Off
        var successCount = 0
        for (channel in 0..15) {
            val sent = sendToKeyboard(byteArrayOf(
                (0xB0 or channel).toByte(),  // Control Change
                122.toByte(),                 // Local Control
                0.toByte()                    // Off
            ))
            if (sent) successCount++
        }
        android.util.Log.d(TAG, "sendLocalControlOff: sent to $successCount/16 channels")
    }

    /**
     * Coupe le volume sur un canal specifique.
     * Alternative a Local Control Off pour les claviers qui ne le supportent pas.
     * Envoie CC7=0 (Volume) et CC11=0 (Expression)
     */
    fun sendChannelMute(channel: Int) {
        // CC7 = Volume (Main Volume)
        sendToKeyboard(byteArrayOf(
            (0xB0 or channel).toByte(),
            7.toByte(),   // Volume
            0.toByte()    // 0 = silence
        ))
        // CC11 = Expression
        sendToKeyboard(byteArrayOf(
            (0xB0 or channel).toByte(),
            11.toByte(),  // Expression
            0.toByte()    // 0 = silence
        ))
    }

    /**
     * Coupe le volume sur tous les canaux.
     * Alternative a Local Control Off pour les claviers qui ne le supportent pas.
     */
    fun sendAllChannelsMute() {
        android.util.Log.i(TAG, "sendAllChannelsMute: sending CC7=0, CC11=0 to all 16 channels")
        for (channel in 0..15) {
            sendChannelMute(channel)
        }
    }

    /**
     * Restaure le volume sur un canal specifique.
     */
    fun sendChannelUnmute(channel: Int) {
        // CC7 = Volume (Main Volume) - restaurer a 100 (valeur typique)
        sendToKeyboard(byteArrayOf(
            (0xB0 or channel).toByte(),
            7.toByte(),   // Volume
            100.toByte()  // Volume normal
        ))
        // CC11 = Expression - restaurer a 127
        sendToKeyboard(byteArrayOf(
            (0xB0 or channel).toByte(),
            11.toByte(),  // Expression
            127.toByte()  // Max expression
        ))
    }

    /**
     * Restaure le volume sur tous les canaux.
     */
    fun sendAllChannelsUnmute() {
        android.util.Log.i(TAG, "sendAllChannelsUnmute: sending CC7=100, CC11=127 to all 16 channels")
        for (channel in 0..15) {
            sendChannelUnmute(channel)
        }
    }

    /**
     * Returns information about the currently connected MIDI keyboard.
     * @return MidiKeyboardInfo or null if no keyboard is connected
     */
    fun getConnectedKeyboardInfo(): MidiKeyboardInfo? {
        val deviceInfo = connectedDeviceInfo ?: return null
        val properties = deviceInfo.properties

        val name = properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: ""
        val manufacturer = properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: ""
        val product = properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: ""

        return MidiKeyboardInfo(
            name = name,
            manufacturer = manufacturer,
            product = product,
            type = deviceInfo.type,
            inputPortCount = deviceInfo.inputPortCount,
            outputPortCount = deviceInfo.outputPortCount
        )
    }

    /**
     * Checks if a MIDI keyboard is currently connected.
     */
    fun isKeyboardConnected(): Boolean = midiDevice != null && connectedDeviceInfo != null

    /**
     * Reactive le synthetiseur local du clavier (Local Control On)
     * Appele quand on quitte le mode pratique pour restaurer le comportement normal.
     */
    fun sendLocalControlOn() {
        android.util.Log.i(TAG, "sendLocalControlOn: sending CC122=127 to all 16 channels")
        // Control Change 122 = Local Control, valeur 127 = On
        var successCount = 0
        for (channel in 0..15) {
            val sent = sendToKeyboard(byteArrayOf(
                (0xB0 or channel).toByte(),  // Control Change
                122.toByte(),                 // Local Control
                127.toByte()                  // On
            ))
            if (sent) successCount++
        }
        android.util.Log.d(TAG, "sendLocalControlOn: sent to $successCount/16 channels")
    }

    companion object {
        private const val TAG = "MidiInputController"
        private const val NOTE_OFF = 0x80
        private const val NOTE_ON = 0x90
        private const val ACTION_USB_PERMISSION = "com.Atom2Universe.app.midi.USB_PERMISSION"
    }
}
