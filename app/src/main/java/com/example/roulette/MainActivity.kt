/**
 * MainActivity.kt - 1 of 2 files for ECE558 Final Project - Roulette
 *
 * @author	Marco Martinez Corral  (marcom@pdx.edu)
 * @date    13-MAR-2025 - 21-MAR-2025 (as of 21-MAR-2025, still waiting for the Nexys A7 hardware piece of the project)
 *
 * @brief
 * MainActivity.kt implements composables for a roulette betting screen that uses the MqttHelper class to publish a JSON object to a HiveMQ Serverless Cluster

 * @detail
 * composable implementation: sets the betting app UI, and establishes mutable lists to keep track of bets, results, and history of both. They are then
 * displayed at the bottom of the screen. Also implements a winning or losing message
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

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mqttHelper = MqttHelper(this)
        mqttHelper.connect()
        setContent {
            RouletteBoard(mqttHelper)
        }
    }
}

//Roulette board function. Includes bets, winning number/color, results coming in from HiveMQ server
//and history of bets and results.
@Composable
fun RouletteBoard(mqttHelper: MqttHelper) {
    val bets = remember { mutableStateMapOf<String, Int>() }
    var winningNumber by remember { mutableStateOf("") }
    var winningColor by remember { mutableStateOf("") }
    var currentBet by remember { mutableStateOf("") }
    var betResult by remember { mutableStateOf("") }
    val resultHistory = remember { mutableStateListOf<String>() }
    val betHistory = remember { mutableStateListOf<String>() }

    // Subscribe to the MQTT result updates.
    LaunchedEffect(Unit) {
        mqttHelper.setOnResultReceived { number, color ->
            winningNumber = number.toString()
            winningColor = color
            betResult = checkBetResult(currentBet, winningNumber, winningColor)
            //this stores the last 5 values of the winning number and color in a list for display
            resultHistory.add("$winningNumber - $winningColor")
            if (resultHistory.size > 5) {
                resultHistory.removeAt(0)
            }

            //Debugging output for Logcat. Needed to see what was actually being recieved and pushed
            //to the MQTT server.
            Log.d("RouletteApp", "Bet result: $betResult")
        }
    }
    //column for the roulette table layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF006400)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Current Bet: $currentBet", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        Text("Winning Number: $winningNumber, Color: $winningColor", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        Text(betResult, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        //numbers laid out in a grid; it was way easier to set the numbers first, then "again" by setting color to red and black. Green was small enough to handle with a case
        val numbers = listOf("0", "00", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36")
        val redNumbers = setOf("1", "3", "5", "7", "9", "12", "14", "16", "18", "19", "21", "23", "25", "27", "30", "32", "34", "36")
        val blackNumbers = setOf("2", "4", "6", "8", "10", "11", "13", "15", "17", "20", "22", "24", "26", "28", "29", "31", "33", "35")

        //sets number color
        Column {
            for (row in numbers.chunked(3)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (number in row) {
                        val backgroundColor = when (number) {
                            "0", "00" -> Color.Green //case for green numbers
                            in redNumbers -> Color.Red
                            in blackNumbers -> Color.Black
                            else -> Color.Gray
                        }
                        Box(
                            modifier = Modifier
                                .size(35.dp)
                                .background(backgroundColor)
                                .clickable {
                                    bets[number] = (bets[number] ?: 0) + 1
                                    val color = when (number) {
                                        "0", "00" -> "green"
                                        in redNumbers -> "red"
                                        in blackNumbers -> "black"
                                        else -> "unknown"
                                    }
                                    currentBet = "Number: $number, Color: $color"
                                    betHistory.add(currentBet)
                                    if (betHistory.size > 5) {
                                        betHistory.removeAt(0)
                                    }
                                    mqttHelper.publishBet(number, color) //publish using mqttHelper class
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(number, color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
        //this handles bets for the color only red/black boxes. it sends an empty JSON object ("") to the
        //server, and the color is what is used for the comparison from the HiveMQ cluster
        Row(modifier = Modifier.padding(top = 16.dp)) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color.Red)
                    .clickable {
                        currentBet = "Color: Red"
                        betHistory.add(currentBet)
                        if (betHistory.size > 5) {
                            betHistory.removeAt(0)
                        }
                        mqttHelper.publishBet("", "red") //empty value for number, since this is a color bet
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Bet", color = Color.White, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color.Black)
                    .clickable {
                        currentBet = "Color: Black"
                        betHistory.add(currentBet)
                        if (betHistory.size > 5) {
                            betHistory.removeAt(0)
                        }
                        mqttHelper.publishBet("", "black") //same, but with black
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Bet", color = Color.White, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        //recent results list and recent bets are displayed in a column, from the saved values in the lists
        Column(modifier = Modifier.fillMaxWidth().background(Color.DarkGray).padding(8.dp)) {
            Text("Recent Results:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            resultHistory.forEach {
                Text(it, fontSize = 14.sp, color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Recent Bets:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            betHistory.forEach {
                Text(it, fontSize = 14.sp, color = Color.White)
            }
        }
    }
}

//function to check if the bet matches the result. this took a lot of troubleshooting using ChatGPT and back and forth bets sending
//because it would only check the color AND number at first. Then, it would expect an empty JSON object from the result, which would never
//come, since the wheel will always send a number, color JSON object
fun checkBetResult(currentBet: String, winningNumber: String, winningColor: String): String {
    val betColor = currentBet.split(", ").last().split(": ").last()
    //winning message. PUBG reference
    return if (betColor.equals(winningColor, ignoreCase = true)) {
        "Winner Winner Chicken Dinner! Winning Color: $winningColor"
    } else {
        "U lose. Winning Color: $winningColor" //womp womp womp...........
    }
}


