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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean


private const val DEFAULT_CACHE_NAME = "cache.cache"
private const val TAG = "RapidsnarkExample"

private const val DEFAULT_CIRCUIT = "credentialAtomicQueryV3OnChain-beta.1"

private val BUNDLED_CIRCUITS = listOf(
    "authV2",
    "authV3",
    "linkedMultiQuery10",
    "credentialAtomicQueryV3OnChain-beta.1",
    "anonAadhaarV1",
    "credential_sha256",
)

private val isProofInProgress = AtomicBoolean(false)

class MainActivity : ComponentActivity() {
    private val inputsUri = mutableStateOf<Uri?>(null)
    private val graphDataUri = mutableStateOf<Uri?>(null)
    private val zkeyUri = mutableStateOf<Uri?>(null)
    private val verificationKeyUri = mutableStateOf<Uri?>(null)

    private val selectedCircuit = mutableStateOf(DEFAULT_CIRCUIT)

    private val errorMessage = mutableStateOf("")
    private val verificationResult = mutableStateOf("")

    private val cacheFileName = mutableStateOf(DEFAULT_CACHE_NAME)

    private val proof = mutableStateOf<ProveResponse?>(null)
    private val executionTimeMs = mutableStateOf(0)
    private val witnessCalcTimeMs = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            android_rapidsnarkTheme {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Example(
                        inputsUri,
                        graphDataUri,
                        zkeyUri,
                        verificationKeyUri,
                        selectedCircuit,
                        cacheFileName,
                        errorMessage,
                        verificationResult,
                        proof,
                        executionTimeMs,
                        witnessCalcTimeMs,
                    )
                }
            }
        }
    }
}

@Composable
fun Example(
    inputsUri: MutableState<Uri?>,
    graphDataUri: MutableState<Uri?>,
    zkeyUri: MutableState<Uri?>,
    verificationKeyUri: MutableState<Uri?>,
    selectedCircuit: MutableState<String>,
    cacheFileName: MutableState<String>,
    error: MutableState<String>,
    verificationResult: MutableState<String>,
    proof: MutableState<ProveResponse?>,
    executionTimeMs: MutableState<Int>,
    witnessCalcTimeMs: MutableState<Int>,
) {
    val context = LocalContext.current
    val scrollState = ScrollState(0)

    val setCacheName: (Uri) -> Unit = {
        val fileName = it.lastPathSegment ?: "cache.zkey"
        val cacheName = if (fileName == "circuit_final.zkey") {
            it.toString().substringBeforeLast('/').substringAfter('/') + ".cache"
        } else {
            fileName.substringBeforeLast('.') + ".cache"
        }
        cacheFileName.value = cacheName
    }

    val zkeyPicker = rememberLauncherForActivityResult(GetCustomContents()) {
        if (it.isNotEmpty()) {
            zkeyUri.value = it.first()
            setCacheName(it.first())
        }
    }
    val inputsPicker = rememberLauncherForActivityResult(GetCustomContents()) {
        if (it.isNotEmpty()) inputsUri.value = it.first()
    }
    val graphDataPicker = rememberLauncherForActivityResult(GetCustomContents()) {
        if (it.isNotEmpty()) graphDataUri.value = it.first()
    }
    val verificationKeyPicker = rememberLauncherForActivityResult(GetCustomContents()) {
        if (it.isNotEmpty()) verificationKeyUri.value = it.first()
    }

    val onProofReady: (ProveResponse, String) -> Unit = { p, circuit ->
        proof.value = p
        selectedCircuit.value = circuit
        verificationResult.value = ""
        error.value = ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(
            title = "Quick prove",
            subtitle = "Generate a proof from a bundled circuit",
        ) {
            for (circuit in BUNDLED_CIRCUITS) {
                Button(
                    onClick = {
                        runBundledProve(
                            context,
                            circuit,
                            cacheFileName.value,
                            onProofReady = { onProofReady(it, circuit) },
                            onTimings = { witness, exec ->
                                witnessCalcTimeMs.value = witness
                                executionTimeMs.value = exec
                                Log.i(TAG, "execution: $exec ms, witness: $witness ms")
                            },
                            onError = { error.value = it },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("⚡  $circuit")
                }
            }
        }

        SectionCard(title = "Actions") {
            Button(
                onClick = {
                    val circuit = selectedCircuit.value
                    makeProof(
                        context,
                        circuit = circuit,
                        zkeyUri = zkeyUri.value,
                        inputsUri = inputsUri.value,
                        graphDataUri = graphDataUri.value,
                        cacheFileName = cacheFileName.value,
                        onProofReady = { onProofReady(it, circuit) },
                        onTimings = { witness, exec ->
                            witnessCalcTimeMs.value = witness
                            executionTimeMs.value = exec
                        },
                        onError = { error.value = it },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Generate proof")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        verifyProof(
                            context,
                            proof = proof.value,
                            selectedCircuit = selectedCircuit.value,
                            verificationKeyUri = verificationKeyUri.value,
                            onResult = { verificationResult.value = it },
                        )
                    },
                    enabled = proof.value != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Validate proof")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        val p = proof.value ?: return@OutlinedButton
                        val text = p.proof + "\n" + p.publicSignals
                        val clipboard =
                            context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("proof", text))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    enabled = proof.value != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = {
                        val p = proof.value ?: return@OutlinedButton
                        val text = p.proof + "\n" + p.publicSignals
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Share proof"))
                    },
                    enabled = proof.value != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share")
                }
            }
        }

        SectionCard(
            title = "Proof",
            subtitle = "Circuit: ${selectedCircuit.value}",
        ) {
            Text(
                text = "Execution: ${formatMs(executionTimeMs.value)}    " +
                        "Witness: ${formatMs(witnessCalcTimeMs.value)}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (error.value.isNotBlank()) {
                Text(
                    text = "Error: ${error.value}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val p = proof.value
            SelectionContainer {
                Text(
                    text = if (p == null) "No proof yet"
                    else "${p.proof}\n${p.publicSignals}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionCard(title = "Verification") {
            SelectionContainer {
                Text(
                    text = verificationResult.value.ifBlank { "No verification yet" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionCard(
            title = "Custom files",
            subtitle = "Override the bundled defaults",
        ) {
            FilePickRow(
                label = "zkey",
                statusText = zkeyUri.value?.lastPathSegment
                    ?: "Default ${selectedCircuit.value}",
                onSelect = { zkeyPicker.launch("application/octet-stream") },
                onReset = { zkeyUri.value = null },
                resetEnabled = zkeyUri.value != null,
            )
            FilePickRow(
                label = "inputs",
                statusText = inputsUri.value?.lastPathSegment
                    ?: "Default ${selectedCircuit.value}",
                onSelect = { inputsPicker.launch("application/json") },
                onReset = { inputsUri.value = null },
                resetEnabled = inputsUri.value != null,
            )
            FilePickRow(
                label = "graph",
                statusText = graphDataUri.value?.lastPathSegment
                    ?: "Default ${selectedCircuit.value}",
                onSelect = { graphDataPicker.launch("application/octet-stream") },
                onReset = { graphDataUri.value = null },
                resetEnabled = graphDataUri.value != null,
            )
            FilePickRow(
                label = "verification key",
                statusText = verificationKeyUri.value?.lastPathSegment
                    ?: "Default ${selectedCircuit.value}",
                onSelect = { verificationKeyPicker.launch("application/json") },
                onReset = { verificationKeyUri.value = null },
                resetEnabled = verificationKeyUri.value != null,
            )
        }

        SectionCard(
            title = "Cache",
            subtitle = "Prover cache speeds up repeated runs",
        ) {
            Text("Cache file: ${cacheFileName.value}")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        val zkey = zkeyUri.value?.lastPathSegment ?: selectedCircuit.value
                        cacheFileName.value = "${zkey}_$stamp.cache"
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Reset")
                }
                OutlinedButton(
                    onClick = {
                        val uri = zkeyUri.value
                        if (uri != null) setCacheName(uri) else cacheFileName.value =
                            DEFAULT_CACHE_NAME
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Default")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun FilePickRow(
    label: String,
    statusText: String,
    onSelect: () -> Unit,
    onReset: () -> Unit,
    resetEnabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(onClick = onSelect, modifier = Modifier.weight(1f)) {
                Text("Select")
            }
            OutlinedButton(
                onClick = onReset,
                enabled = resetEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Text("Reset")
            }
        }
    }
}

private fun formatMs(ms: Int): String =
    if (ms == 0) "—" else if (ms < 1000) "$ms ms" else "%.3f s".format(ms / 1000.0)

@SuppressLint("UnrememberedMutableState")
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    android_rapidsnarkTheme {
        Example(
            mutableStateOf(null),
            mutableStateOf(null),
            mutableStateOf(null),
            mutableStateOf(null),
            mutableStateOf(DEFAULT_CIRCUIT),
            mutableStateOf(DEFAULT_CACHE_NAME),
            mutableStateOf(""),
            mutableStateOf(""),
            mutableStateOf(null),
            mutableStateOf(0),
            mutableStateOf(0),
        )
    }
}

class GetCustomContents(
    private val isMultiple: Boolean = false,
) : ActivityResultContract<String, List<@JvmSuppressWildcards Uri>>() {

    override fun createIntent(context: Context, input: String): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, isMultiple)
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
        internal fun Intent.getClipDataUris(): List<Uri> {
            val resultSet = LinkedHashSet<Uri>()
            data?.let { resultSet.add(it) }
            val clipData = clipData
            if (clipData == null && resultSet.isEmpty()) {
                return emptyList()
            } else if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    if (uri != null) resultSet.add(uri)
                }
            }
            return ArrayList(resultSet)
        }
    }
}

private fun runBundledProve(
    context: Context,
    circuit: String,
    cacheFileName: String,
    onProofReady: (ProveResponse) -> Unit,
    onTimings: (witness: Int, exec: Int) -> Unit,
    onError: (String) -> Unit,
) {
    makeProof(
        context,
        circuit = circuit,
        zkeyUri = null,
        inputsUri = null,
        graphDataUri = null,
        cacheFileName = cacheFileName,
        onProofReady = onProofReady,
        onTimings = onTimings,
        onError = onError,
    )
}

private fun makeProof(
    context: Context,
    circuit: String,
    zkeyUri: Uri?,
    inputsUri: Uri?,
    graphDataUri: Uri?,
    cacheFileName: String,
    onProofReady: (ProveResponse) -> Unit,
    onTimings: (witness: Int, exec: Int) -> Unit,
    onError: (String) -> Unit,
) {
    if (!isProofInProgress.compareAndSet(false, true)) {
        Toast.makeText(context, "Proof already in progress", Toast.LENGTH_SHORT).show()
        return
    }

    val inputs = try {
        if (inputsUri == null) {
            context.assets.open("${circuit}_inputs.json").readContents()
        } else {
            context.contentResolver.openInputStream(inputsUri)!!.readContents()
        }
    } catch (e: IOException) {
        isProofInProgress.set(false)
        onError("Failed to load inputs: ${e.message}")
        return
    }

    val graphData = try {
        if (graphDataUri == null) {
            context.assets.open("$circuit.wcd").loadIntoBytes()
        } else {
            context.contentResolver.openInputStream(graphDataUri)!!.loadIntoBytes()
        }
    } catch (e: IOException) {
        isProofInProgress.set(false)
        onError("Failed to load graph data: ${e.message}")
        return
    }

    var executionStart = System.currentTimeMillis()
    val witness = calculateWitness(inputs = inputs, graphData = graphData)
    val witnessTime = (System.currentTimeMillis() - executionStart).toInt()

    val zkeyFilePath: String = try {
        if (zkeyUri == null) {
            val zkeyFile = File(context.cacheDir, "$circuit.zkey")
            if (!zkeyFile.exists()) {
                context.assets.open("$circuit.zkey").use { input ->
                    zkeyFile.outputStream().use { input.copyTo(it) }
                }
            }
            zkeyFile.path
        } else {
            val documentFile = DocumentFile.fromSingleUri(context, zkeyUri)
            val fileName = documentFile?.name ?: "custom.zkey"
            val zkeyFile = File(context.cacheDir, fileName)
            if (!zkeyFile.exists()) {
                context.contentResolver.openInputStream(zkeyUri)!!.use { input ->
                    zkeyFile.outputStream().use { input.copyTo(it) }
                }
            }
            zkeyFile.path
        }
    } catch (e: IOException) {
        isProofInProgress.set(false)
        onError("Failed to load zkey: ${e.message}")
        return
    }

    val cachePath = File(context.cacheDir, cacheFileName).absolutePath

    Thread {
        try {
            executionStart = System.currentTimeMillis()
            val proof = groth16Prove(zkeyFilePath, witness, cachePath = cachePath)
            val execTime = (System.currentTimeMillis() - executionStart).toInt()

            Handler(Looper.getMainLooper()).post {
                onTimings(witnessTime, execTime)
                onProofReady(proof)
            }
        } catch (t: Throwable) {
            val msg = t.message ?: "Proof generation failed"
            Log.e(TAG, "makeProof failed", t)
            Handler(Looper.getMainLooper()).post {
                onError(msg)
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        } finally {
            isProofInProgress.set(false)
        }
    }.start()
}

private fun verifyProof(
    context: Context,
    proof: ProveResponse?,
    selectedCircuit: String,
    verificationKeyUri: Uri?,
    onResult: (String) -> Unit,
) {
    if (proof == null) {
        onResult("No proof to verify")
        return
    }

    val start = System.currentTimeMillis()
    val vk: String = try {
        if (verificationKeyUri != null) {
            context.contentResolver.openInputStream(verificationKeyUri)!!.readContents()
        } else {
            context.assets.open("${selectedCircuit}_verification_key.json").readContents()
        }
    } catch (e: IOException) {
        onResult("No verification key bundled for $selectedCircuit")
        return
    }

    try {
        val valid = groth16Verify(
            proof = proof.proof,
            inputs = proof.publicSignals,
            verificationKey = vk,
        )
        val elapsed = (System.currentTimeMillis() - start).toInt()
        onResult(
            if (valid) "Result: ✓ valid (${formatMs(elapsed)})"
            else "Result: ✗ invalid (${formatMs(elapsed)})"
        )
    } catch (e: Exception) {
        onResult("Verification error: ${e.message ?: "unknown"}")
    }
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
