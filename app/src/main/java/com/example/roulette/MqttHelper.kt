/**
 * MqttHelper.kt - 2 of 2 files for ECE558 Final Project - Roulette
 *
 * @author	Marco Martinez Corral  (marcom@pdx.edu)
 * @date    13-MAR-2025 - 21-MAR-2025 (as of 21-MAR-2025, still waiting for the Nexys A7 hardware piece of the project)
 *
 * @brief
 * MqttHelper.kt implements a class that connects to a HiveMQ Serverless Cluster and publishes and subscribes to MQTT topics via SSL. It receives and sends data in JSON object
 * format to the server, for use in MainActivity.kt

 * @detail
 * composable implementation: sets the betting app UI, and establishes mutable lists to keep track of bets, results, and history of both. They are then
 *
 *
 *
 * Revision History
 * ----------------
 * 15-Jan-2024	RK	Created this starter code template
 *13-MAR-2025 - Marco Martinez Corral started writing code for roulette app
 * 21-MAR-2025 - Marco Martinez Corral finished writing code for roulette app. No hardware implementation from the Nexys A7 part of the project was provided
 *
 * @note:
 *     ChatGPT-4 used for composable help and generation, as well as extensive troubleshooting on the check bet result function.
 *     Gemini used for font and colors (using built in functionality on android studio)
 *     hackernoon.com helped me implement text fields and the composable basics
 *     https://hackernoon.com/the-ultimate-jetpack-compose-cheat-sheet
 *
 *     This application was written entirely by Marco Martinez Corral - ECE558
*/


package com.example.roulette

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

//very similar MQTT helper class from the weather station. I used the same server and same credentials for the AndroidClient
class MqttHelper(context: Context) {
    private val brokerUrl = "ssl://18a8caf27c6b40449526c0491ea5c727.s1.eu.hivemq.cloud:8883"
    private val clientId = "AndroidClient"
    private val username = "phone1"
    private val password = "Q11plebada"

    private var mqttClient: MqttClient? = null
    private var resultListener: ((Int, String) -> Unit)? = null
    private var connectionListener: ((Boolean) -> Unit)? = null
    //waits for connection
    fun setOnResultReceived(listener: (Int, String) -> Unit) {
        resultListener = listener
    }
    //connection status
    fun setOnConnectionChanged(listener: (Boolean) -> Unit) {
        connectionListener = listener
    }
    //this is needed for the app to not crash on connecting "lag", since it requires a connection to the server, but it will not
    //establish it immediatelly
    fun connect() {
        try {
            val persistence = MemoryPersistence()
            mqttClient = MqttClient(brokerUrl, clientId, persistence)
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                userName = username
                password = this@MqttHelper.password.toCharArray() //converts the HiveMQ password to a char array
            }
            //sets the JSON formatting for the bets.
            mqttClient?.setCallback(object : MqttCallback {
                override fun messageArrived(topic: String, message: MqttMessage) {
                    if (topic == "result/data") { //the phone will always send receive result/data from the server
                        try {
                            val json = JSONObject(message.toString())
                            val number = json.getInt("number")
                            val color = json.getString("color")
                            resultListener?.invoke(number, color)
                        } catch (e: Exception) {
                            Log.e("MQTT", "Error parsing JSON: ${e.message}") //error handling for bad JSON request. I had this setup in case the raspberry pi pico
                                                                                        //sent bad data, since it's expecting a certain format
                        }
                    }
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.e("MQTT", "Connection lost: ${cause?.message}") //error handling for lost connection. absolutely needed, and actually saves the main thread from crashing
                    connectionListener?.invoke(false)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.d("MQTT", "Message delivered") //needed to have this for Logcat debug
                }
            })
            //connection status
            mqttClient?.connect(options)
            mqttClient?.subscribe("result/data")
            Log.d("MQTT", "Connected successfully")
            connectionListener?.invoke(true)
        } catch (e: MqttException) {
            Log.e("MQTT", "Error connecting: ${e.message}")
            connectionListener?.invoke(false)
        }
    }
    //not used on final implementations. I kept it from the weather app to have a "reset connection button" that never came to fruition
    fun disconnect() {
        try {
            mqttClient?.disconnect()
            Log.d("MQTT", "Disconnected successfully")
            connectionListener?.invoke(false)
        } catch (e: MqttException) {
            Log.e("MQTT", "Error disconnecting: ${e.message}")
        }
    }
    //sends the bets to the hivemq server
    fun publishBet(number: String, color: String) {
        try {
            val json = JSONObject().apply {
                put("number", number)
                put("color", color)
            }
            val mqttMessage = MqttMessage(json.toString().toByteArray())
            mqttClient?.publish("bet/data", mqttMessage)
            Log.d("MQTT", "Bet published: $json")
        } catch (e: MqttException) {
            Log.e("MQTT", "Error publishing bet: ${e.message}") //error handling, again
        }
    }
}
