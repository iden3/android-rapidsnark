package com.example.rapidsnark_example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.rapidsnark_example.ui.theme.Rapidsnark_exampleTheme
import com.iden3.rapidsnark.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


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

var executionTime = 0

@Composable
fun Greeting() {
    val context = LocalContext.current

    var proof by remember { mutableStateOf("") }
    var publicSignals by remember { mutableStateOf("") }
    var useFileProver by remember { mutableStateOf(true) }

    val execTimeSeconds = executionTime / 1000.0

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use file prover")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = useFileProver,
                onCheckedChange = { useFileProver = it },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Execution time: $execTimeSeconds seconds",
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            modifier = Modifier.fillMaxHeight(fraction = 0.6f),
            text = "$proof\n$publicSignals",
        )
        Button(
            onClick = {
                val response = makeProof(context, useFileProver)

                proof = response.proof
                publicSignals = response.publicSignals
            },
        ) {
            Text("Calculate Proof")
        }
        Button(
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

fun makeProof(context: Context, useFileProver: Boolean): ProveResponse {
    val witness = readFileFromAssets(context, "witness.wtns")

    val executionStart: Long

    val result = if (useFileProver) {
        val zkeyFilePath = context.externalCacheDir!!.absolutePath + "/circuit_final.zkey"
        val zkeyFile = File(zkeyFilePath)
        if (!zkeyFile.exists()) {
            val zkeyInputStream = context.assets.open("circuit_final.zkey")
            val zkeyOutputStream = zkeyFile.outputStream()
            copyFile(zkeyInputStream, zkeyOutputStream)
        }

        executionStart = System.currentTimeMillis()

        groth16ProveWithZKeyFilePath(zkeyFilePath, witness)
    } else {
        val zkey: ByteArray = readFileFromAssets(context, "circuit_final.zkey")

        executionStart = System.currentTimeMillis()

        groth16Prove(zkey, witness)
    }

    executionTime = (System.currentTimeMillis() - executionStart).toInt()

    return result
}

@Throws(IOException::class)
private fun copyFile(`in`: InputStream, out: OutputStream) {
    val buffer = ByteArray(1024)
    var read: Int
    while (`in`.read(buffer).also { read = it } != -1) {
        out.write(buffer, 0, read)
    }
}

fun readFileFromAssets(context: Context, assetName: String): ByteArray {
    val inputStream: InputStream = context.assets.open(assetName)

    val buffer = ByteArray(16384)
    val output = ByteArrayOutputStream()
    var bytesRead: Int
    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        output.write(buffer, 0, bytesRead)
    }

    return output.toByteArray()
}
