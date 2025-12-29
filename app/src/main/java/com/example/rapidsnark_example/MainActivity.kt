package com.example.rapidsnark_example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.android_rapidsnark.ui.theme.android_rapidsnarkTheme
import io.iden3.circomwitnesscalc.calculateWitness
import io.iden3.rapidsnark.ProveResponse
import io.iden3.rapidsnark.groth16Prove
import io.iden3.rapidsnark.groth16Verify
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader


class MainActivity : ComponentActivity() {
    private var inputsUri = mutableStateOf<Uri?>(null)
    private var graphDataUri = mutableStateOf<Uri?>(null)
    private var zkeyUri = mutableStateOf<Uri?>(null)

    private var errorMessage = mutableStateOf("")

    private val proof = mutableStateOf<ProveResponse?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            android_rapidsnarkTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Example(
                        inputsUri,
                        graphDataUri,
                        zkeyUri,
                        errorMessage,
                        proof,
                    )
                }
            }
        }
    }
}

var witnessCalcTime = 0
var executionTime = 0

@Composable
fun Example(
    inputsUri: MutableState<Uri?>,
    graphDataUri: MutableState<Uri?>,
    zkeyUri: MutableState<Uri?>,
    error: MutableState<String>,
    proof: MutableState<ProveResponse?>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val scrollState = ScrollState(0)

    val hasCustomZkey = zkeyUri.value != null
    val hasCustomInputs = inputsUri.value != null
    val hasCustomGraphData = graphDataUri.value != null

    val zkeyPicker = rememberLauncherForActivityResult(GetCustomContents()) {
        if (it.isNotEmpty()) {
            zkeyUri.value = it.first()
        }
    }
    val inputsPicker = rememberLauncherForActivityResult(GetCustomContents()) {
        if (it.isNotEmpty()) {
            inputsUri.value = it.first()
        }
    }
    val graphDataPicker = rememberLauncherForActivityResult(GetCustomContents()) {
        if (it.isNotEmpty()) {
            graphDataUri.value = it.first()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
            .then(modifier),
    ) {
        Text(if (hasCustomZkey) "Custom zkey from ${zkeyUri.value}" else "Default authV2 zkey selected")
        Row(horizontalArrangement = Arrangement.SpaceAround) {
            Button(onClick = { zkeyPicker.launch("application/octet-stream") }) {
                Text("Select zkey")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { zkeyUri.value = null }, enabled = zkeyUri.value != null) {
                Text("Reset zkey")
            }
        }
        Text(if (hasCustomInputs) "Custom inputs from ${inputsUri.value}" else "Default authV2 inputs selected")
        Row(horizontalArrangement = Arrangement.SpaceAround) {
            Button(onClick = { inputsPicker.launch("application/json") }) {
                Text("Select inputs")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { inputsUri.value = null }, enabled = inputsUri.value != null) {
                Text("Reset inputs")
            }
        }
        Text(if (hasCustomGraphData) "Custom graph data from ${graphDataUri.value}" else "Default authV2 graph data selected")
        Row(horizontalArrangement = Arrangement.SpaceAround) {
            Button(onClick = { graphDataPicker.launch("application/octet-stream") }) {
                Text("Select graph data")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { graphDataUri.value = null }, enabled = graphDataUri.value != null) {
                Text("Reset graph data")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (error.value.isNotBlank())
            Text(
                "Error: " + error.value
            )
        Text(
            text = "Execution time: $executionTime millis\nWitness calculation time: $witnessCalcTime millis",
        )
        Spacer(modifier = Modifier.height(8.dp))


        Button(
            onClick = {
                makeProof(
                    context,
                    zkeyUri.value,
                    inputsUri.value,
                    graphDataUri.value,
                    onProofReady = { proof.value = it }
                )
            },
        ) {
            Text("Calculate Proof")
        }
        if (proof.value != null)
            Button(
                onClick = {
                    val vk = context.assets.open("authV2_verification_key.json").readContents()
                    try {
                        val valid = groth16Verify(
                            proof = proof.value!!.proof,
                            inputs = proof.value!!.publicSignals,
                            verificationKey = vk,
                        )

                        if (valid) {
                            error.value = "Proof is valid"
                        } else {
                            error.value = "Proof is NOT valid"
                        }
                    } catch (e: Exception) {
                        error.value = e.message ?: "Unknown verification error"
                        return@Button
                    }

                },
            ) {
                Text("Verify Proof")
            }
        Spacer(modifier = Modifier.height(8.dp))
        if (proof.value != null)
            Button(
                onClick = {
                    val text = proof.value!!.component1() + "\n" + proof.value!!.component2()

                    val clipboardService =
                        context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboardService.setPrimaryClip(
                        ClipData.newPlainText(text, text)
                    )
                },
            ) {
                Text("Copy proof to clipboard")
            }
        Spacer(modifier = Modifier.height(8.dp))
        if (proof.value != null)
            SelectionContainer {
                Text(
                    modifier = Modifier.fillMaxHeight(fraction = 0.6f),
                    text = "${proof.value!!.proof}\n${proof.value!!.publicSignals}",
                )
            }

    }
}

@SuppressLint("UnrememberedMutableState")
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    android_rapidsnarkTheme {
        Example(
            mutableStateOf(null),
            mutableStateOf(null),
            mutableStateOf(null),
            mutableStateOf(""),
            mutableStateOf(null),
        )
    }
}

class GetCustomContents(
    private val isMultiple: Boolean = false, //This input check if the select file option is multiple or not
) : ActivityResultContract<String, List<@JvmSuppressWildcards Uri>>() {

    override fun createIntent(context: Context, input: String): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input //The input option es the MIME Type that you need to use
            putExtra(Intent.EXTRA_LOCAL_ONLY, true) //Return data on the local device
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, isMultiple) //If select one or more files
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        return intent.takeIf {
            resultCode == Activity.RESULT_OK
        }?.getClipDataUris() ?: emptyList()
    }

    internal companion object {

        //Collect all Uris from files selected
        internal fun Intent.getClipDataUris(): List<Uri> {
            // Use a LinkedHashSet to maintain any ordering that may be
            // present in the ClipData
            val resultSet = LinkedHashSet<Uri>()
            data?.let { data ->
                resultSet.add(data)
            }
            val clipData = clipData
            if (clipData == null && resultSet.isEmpty()) {
                return emptyList()
            } else if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    if (uri != null) {
                        resultSet.add(uri)
                    }
                }
            }
            return ArrayList(resultSet)
        }
    }
}

var proofCalcThread: Thread? = null

fun makeProof(
    context: Context,
    zkeyUri: Uri?,
    inputsUri: Uri?,
    graphDataUri: Uri?,
    onProofReady: (ProveResponse) -> Unit
) {
    val inputs = if (inputsUri == null) {
        context.assets.open("authV2_inputs.json").readContents()
    } else {
        context.contentResolver.openInputStream(inputsUri)!!.readContents()
    }

    val graphData = if (graphDataUri == null) {
        context.assets.open("authV2.wcd").loadIntoBytes()
    } else {
        context.contentResolver.openInputStream(graphDataUri)!!.loadIntoBytes()
    }

    var executionStart = System.currentTimeMillis()

    val witness = calculateWitness(inputs = inputs, graphData = graphData)

    witnessCalcTime = (System.currentTimeMillis() - executionStart).toInt()

    val zkeyFilePath: String

    if (zkeyUri == null) {
        // Copy authV2 file from assets to cache folder and use it
        val zkeyFile = File(context.cacheDir, "authV2.zkey")
        if (!zkeyFile.exists()) {
            context.assets.open("authV2.zkey").use {
                zkeyFile.outputStream().use { outputStream ->
                    it.copyTo(outputStream)
                }
            }
        }
        zkeyFilePath = zkeyFile.path
    } else {
        // Copy zkey file from uri to cache folder and use it
        val documentFile = DocumentFile.fromSingleUri(context, zkeyUri)
        val fileName = documentFile!!.name

        val zkeyFile = File(context.cacheDir, fileName!!)
        if (!zkeyFile.exists()) {
            context.contentResolver.openInputStream(zkeyUri)!!.use {
                zkeyFile.outputStream().use { outputStream ->
                    it.copyTo(outputStream)
                }
            }
        }
        zkeyFilePath = zkeyFile.path
    }

    proofCalcThread = Thread {
        executionStart = System.currentTimeMillis()
        val proof = groth16Prove(zkeyFilePath, witness)

        executionTime = (System.currentTimeMillis() - executionStart).toInt()

        proofCalcThread = null

        onProofReady(proof)
    }
    proofCalcThread?.start()
}

private fun InputStream.loadIntoBytes(): ByteArray {
    use {
        val buf = ByteArray(available())
        read(buf)
        return buf
    }
}

private fun InputStream.readContents(): String {
    use {
        BufferedReader(InputStreamReader(this)).use {
            val sb = StringBuilder()
            var s = it.readLine()
            while (s != null) {
                sb.append(s)
                s = it.readLine()
            }
            return sb.toString()
        }
    }
}
