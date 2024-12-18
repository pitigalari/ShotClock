package com.roshsoft.timerremote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence


class MainActivity : ComponentActivity() {

    lateinit var toolbar: MaterialToolbar;
    lateinit var conStatusImg: ImageView;

    var pin: String? = null;
    var connectionStatus: String = "Disconnected";

    private val TOPIC_SUFFIX = "3f191e8fbbe6aff775b68c5f538d9c5c"
    lateinit var mqttClient: MqttClient
    var topic: String = TOPIC_SUFFIX

    lateinit var ui: Handler;

    var timestampLocked: Long = 0;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.main_activity);
        ui = Handler(mainLooper)

        toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(Toolbar.OnMenuItemClickListener { item: MenuItem? ->
            when (item?.itemId) {
                R.id.menu_connect -> {
                    connect()
                    return@OnMenuItemClickListener true
                }

                R.id.menu_disconnect -> {
                    disconnect()
                    return@OnMenuItemClickListener true
                }

                R.id.menu_pin -> {
                    setPin()
                    return@OnMenuItemClickListener true
                }
            }
            false
        })

        conStatusImg = findViewById(R.id.connectionStatus)
        conStatusImg.setOnClickListener(View.OnClickListener { v: View? ->
            Toast.makeText(this, connectionStatus, Toast.LENGTH_SHORT).show()
        })

        findViewById<MaterialButton>(R.id.btn_start).setOnClickListener { publish("start") }
        findViewById<MaterialButton>(R.id.btn_stop).setOnClickListener { publish("stop") }
        findViewById<MaterialButton>(R.id.btn_reset).setOnClickListener { publish("reset") }

        setupMqttClient()
    }

    override fun onBackPressed() {
        finish()
        super.onBackPressed()
    }

    override fun onDestroy() {
        try {
            disconnect(true)
            mqttClient.close(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    private fun setupMqttClient() {

        val cId = "ifs-${System.nanoTime()}"

        val desc = findViewById<MaterialTextView>(R.id.connectionDetail);
        desc.text = cId
        desc.setOnLongClickListener {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                ClipData.newPlainText("label", cId)
            )
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
            true
        }

        mqttClient = object : MqttClient(
            "tcp://broker.hivemq.com:1883",
            cId,
            MemoryPersistence()
        ) {
            override fun publish(topic: String?, message: MqttMessage?) {
                println("### MQTT SEND -> T: $topic | M: $message");
                super.publish(topic, message)
            }
        }

        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectionLost(cause: Throwable?) {
                System.err.println("### MQTT DISCONNECTED -> ${cause?.toString()}")
                ui.post { onDisconnected() }
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (message != null)
                    println(
                        "### MQTT RECEIVED -> T: $topic | QoS: ${message.qos} | M: ${
                            String(
                                message.payload
                            )
                        }"
                    );
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
            }

            override fun connectComplete(autoReconnect: Boolean, serverURI: String?) {
                println("### MQTT CONNECTED -> reconnect: $autoReconnect | URI: $serverURI")

//                if (!autoReconnect) {
//                    try {
//                        mqttClient.subscribe(topic, 0)
//                        println("### MQTT SUBSCRIBED -> T: $topic")
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                        return
//                    }
//                }

                ui.post { onConnected(autoReconnect) }
            }
        })

        println("### MQTT Client ID: $cId");
    }

    private fun connect() {
        connectionStatus = "Connecting..."
        conStatusImg.setBackgroundResource(R.drawable.ic_connecting)

        Thread(Runnable {
            var options = MqttConnectOptions()
            options.keepAliveInterval = 10
            options.isAutomaticReconnect = true
            mqttClient.connect(options)
        }).start()
    }

    private fun disconnect(force: Boolean = false) {
        if (mqttClient.isConnected) {
            if (force)
                mqttClient.disconnectForcibly()
            else
                mqttClient.disconnect()
            println("### MQTT DISCONNECTED -> forcefully: $force")
            onDisconnected()
            Toast.makeText(this, "Disconnected!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setPin() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Enter PIN")

        val input = EditText(this)
        if (pin != null)
            input.setText(pin)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        builder.setView(input)

        builder.setPositiveButton("Connect",
            DialogInterface.OnClickListener { dialog, _ ->
                disconnect()
                pin = input.text.toString()
                toolbar.subtitle = pin;
                topic = TOPIC_SUFFIX + pin?.let { "/$pin" }
                connect()
            })
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun onConnected(reconnect: Boolean = false) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        connectionStatus = "Connected"
        conStatusImg.setBackgroundResource(R.drawable.ic_connected)
        if (!reconnect)
            Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
    }

    private fun onDisconnected() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        connectionStatus = "Disconnected"
        conStatusImg.setBackgroundResource(R.drawable.ic_diconnected)
    }

    private fun publish(message: String) {
        val now = System.currentTimeMillis();
        if (timestampLocked > now)
            return;
        timestampLocked = now + 350; // 350ms delay between clicks to prevent multi taps

        if (!mqttClient.isConnected) {
            Toast.makeText(this, "ERR: MQTT Client is not connected.", Toast.LENGTH_LONG).show()
            setPin()
            return
        }

        val msg = MqttMessage()
        msg.qos = 0
        msg.payload = message.toByteArray()
        mqttClient.publish(topic, msg)
    }
}