package com.example.rapidsnark_example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.rapidsnark_example.ui.theme.Rapidsnark_exampleTheme

import com.iden3.rapidsnark.*
import java.io.ByteArrayOutputStream


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Rapidsnark_exampleTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting()
                }
            }
        }
    }
}

@Composable
fun Greeting() {
    val context = LocalContext.current

    var proof by remember { mutableStateOf("") }
    var publicSignals by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = proof + "\n" + publicSignals,
        )
        Button(
            modifier = Modifier.weight(0f),
            onClick = {
                val response = makeProof(context)

                proof = response.proof
                publicSignals = response.publicSignals
            },
        ) {
            Text("Calculate Proof")
        }
        Button(
            modifier = Modifier.weight(0f),
            onClick = {
                val text = proof + "\n" + publicSignals

                val clipboardService =
                    context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboardService.setPrimaryClip(
                    ClipData.newPlainText(text, text)
                )
            },
        ) {
            Text("Copy proof to clipboard")
        }
    }

}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Rapidsnark_exampleTheme {
        Greeting()
    }
}

fun makeProof(context: Context): ProveResponse {
    val zkey: ByteArray = readFileFromAssets(context, "circuit_final.zkey")
    val witness = readFileFromAssets(context, "witness.wtns")

    return groth16Prove(zkey, witness)
}

fun readFileFromAssets(context: Context, assetName: String): ByteArray {
    val fileDescriptor = context.assets.openFd(assetName)
    val inputStream = fileDescriptor.createInputStream()

    val buffer = ByteArray(16384)
    val output = ByteArrayOutputStream()
    var bytesRead: Int
    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        output.write(buffer, 0, bytesRead)
    }

    return output.toByteArray()
}