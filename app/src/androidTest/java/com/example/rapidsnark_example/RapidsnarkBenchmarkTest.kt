package com.example.rapidsnark_example

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.iden3.circomwitnesscalc.calculateWitness
import io.iden3.rapidsnark.groth16Prove
import io.iden3.rapidsnark.groth16Verify
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * Benchmark driver run on-device. For each circuit folder under
 * <app external files dir>/testdata2/ that has zkey + wcd + input.json, runs one
 * warm-up prove and [ITERATIONS] measured proves, recording witness-calc and
 * prove timings into a CSV under <app external files dir>/benchmark_results/.
 *
 * Instrumentation args:
 *   -e libVariant <old|new>        Tagged in every CSV row. Default "unknown".
 *   -e iterations <n>              Override measured iteration count. Default 5.
 *   -e circuitFilter <a,b,c>       Only run circuits whose name contains ANY listed substring.
 *   -e circuitCooldownSec <n>      Uniform cooldown between circuits. Default 0.
 *   -e smallCircuitCooldownSec <n> Cooldown before a "small" circuit (zkey < bigThresholdMb).
 *   -e bigCircuitCooldownSec <n>   Cooldown before a "big" circuit (zkey >= bigThresholdMb).
 *   -e bigThresholdMb <mb>         zkey size threshold in MB. Default 50.
 *     (If small/big are set, they override the uniform circuitCooldownSec.)
 *
 * Pushed testdata layout (per circuit folder):
 *   testdata2/<circuit>/
 *     circuit_final.zkey          (or <circuit>.zkey)
 *     <circuit>.wcd
 *     input.json
 *     <circuit>_verification_key.json  (optional)
 */
@RunWith(AndroidJUnit4::class)
class RapidsnarkBenchmarkTest {

    companion object {
        private const val TAG = "RapidsnarkBench"
        private const val DEFAULT_ITERATIONS = 5
        private const val WARMUP_ITERATIONS = 0
    }

    @Test
    fun benchmarkAllCircuits() {
        val args = InstrumentationRegistry.getArguments()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        val libVariant = args.getString("libVariant") ?: "unknown"
        val iterations = args.getString("iterations")?.toIntOrNull() ?: DEFAULT_ITERATIONS
        val circuitFilters = args.getString("circuitFilter")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val uniformCooldown = args.getString("circuitCooldownSec")?.toIntOrNull() ?: 3
        val smallCooldownSec = args.getString("smallCircuitCooldownSec")?.toIntOrNull() ?: uniformCooldown
        val bigCooldownSec = args.getString("bigCircuitCooldownSec")?.toIntOrNull() ?: uniformCooldown
        val bigThresholdBytes = (args.getString("bigThresholdMb")?.toIntOrNull() ?: 50) * 1_000_000L

        val testdataRoot = File(ctx.getExternalFilesDir(null), "testdata2")
        assertTrue(
            "testdata2 missing at ${testdataRoot.absolutePath}. Push it via adb first.",
            testdataRoot.isDirectory
        )

        val resultsDir = File(ctx.getExternalFilesDir(null), "benchmark_results").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val csvFile = File(resultsDir, "${libVariant}_${stamp}.csv")

        val circuits = testdataRoot.listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name }
            ?: emptyList()

        Log.i(TAG, "libVariant=$libVariant iterations=$iterations cooldown_small=${smallCooldownSec}s cooldown_big=${bigCooldownSec}s bigThreshold=${bigThresholdBytes / 1_000_000}MB circuits=${circuits.size}")
        if (circuitFilters.isNotEmpty()) Log.i(TAG, "circuitFilters=$circuitFilters")
        Log.i(TAG, "csv=${csvFile.absolutePath}")

        csvFile.bufferedWriter().use { out ->
            out.write("libVariant,circuit,iteration,witnessCalcMs,proveMs,verifyOk,zkeyBytes,proofBytes,publicSignalsBytes\n")

            var benchmarked = 0
            var skipped = 0

            for (circuitDir in circuits) {
                val name = circuitDir.name
                if (circuitFilters.isNotEmpty() && circuitFilters.none { name.contains(it) }) {
                    continue
                }

                // Pick cooldown based on (upcoming) circuit's zkey size: small vs big.
                val upcomingZkey = circuitDir.listFiles()?.firstOrNull { it.name.endsWith(".zkey") }
                val isBig = (upcomingZkey?.length() ?: 0L) >= bigThresholdBytes
                val cooldown = if (isBig) bigCooldownSec else smallCooldownSec
                if (benchmarked > 0 && cooldown > 0) {
                    val bucket = if (isBig) "big" else "small"
                    Log.i(TAG, "cooldown ${cooldown}s ($bucket) before $name ...")
                    Thread.sleep(cooldown * 1000L)
                }

                val files = circuitDir.listFiles() ?: emptyArray()
                val zkey = files.firstOrNull { it.name.endsWith(".zkey") }
                val wcd = files.firstOrNull { it.name.endsWith(".wcd") }
                val input = files.firstOrNull { it.name == "input.json" }
                val vkey = files.firstOrNull {
                    it.name.endsWith("_verification_key.json") || it.name.endsWith("_vkey.json")
                }

                if (zkey == null || wcd == null || input == null) {
                    Log.w(
                        TAG,
                        "skip $name  (zkey=${zkey != null} wcd=${wcd != null} input=${input != null})"
                    )
                    skipped++
                    continue
                }

                Log.i(
                    TAG,
                    "=== $name  zkey=${zkey.length() / 1_000_000}MB  wcd=${wcd.length() / 1000}KB ==="
                )

                try {
                    runCircuit(
                        libVariant = libVariant,
                        name = name,
                        zkey = zkey,
                        wcd = wcd,
                        input = input,
                        vkey = vkey,
                        iterations = iterations,
                        out = out,
                        cacheDir = ctx.cacheDir,
                    )
                    benchmarked++
                } catch (t: Throwable) {
                    Log.e(TAG, "FAILED $name: ${t.message}", t)
                    out.write("$libVariant,$name,-1,-1,-1,ERROR:${t.javaClass.simpleName},${zkey.length()},0,0\n")
                    out.flush()
                }
            }

            Log.i(TAG, "DONE. benchmarked=$benchmarked skipped=$skipped")
        }

        Log.i(TAG, "CSV written: ${csvFile.absolutePath}")
    }

    private fun runCircuit(
        libVariant: String,
        name: String,
        zkey: File,
        wcd: File,
        input: File,
        vkey: File?,
        iterations: Int,
        out: java.io.Writer,
        cacheDir: File,
    ) {
        // Load inputs + graph data once per circuit. These are small (< a few MB)
        // and we don't want I/O to dominate repeated runs.
        val inputsStr = input.readText()
        val graphData = wcd.readBytes()
        val vkeyStr = vkey?.readText()

        val cachePath = File(cacheDir, "${zkey.nameWithoutExtension}.cache").absolutePath
        val witnessCalcs = ArrayList<Double>(iterations)
        val proves = ArrayList<Double>(iterations)

        repeat(WARMUP_ITERATIONS + iterations) { i ->
            val measured = i >= WARMUP_ITERATIONS
            val t0 = System.nanoTime()
            val witness = calculateWitness(inputs = inputsStr, graphData = graphData)
            val witnessMs = (System.nanoTime() - t0) / 1_000_000.0

            val t1 = System.nanoTime()
            val proof = groth16Prove(zkey.absolutePath, witness, cachePath = cachePath)
            val proveMs = (System.nanoTime() - t1) / 1_000_000.0

            var verifyOk = "na"
            if (vkeyStr != null) {
                verifyOk = try {
                    if (groth16Verify(proof.proof, proof.publicSignals, vkeyStr)) "true" else "false"
                } catch (t: Throwable) {
                    "ERROR:${t.javaClass.simpleName}"
                }
            }

            if (measured) {
                val iter = i - WARMUP_ITERATIONS + 1
                witnessCalcs += witnessMs
                proves += proveMs
                out.write(
                    "$libVariant,$name,$iter,${"%.3f".format(witnessMs)},${"%.3f".format(proveMs)}," +
                            "$verifyOk,${zkey.length()},${proof.proof.length},${proof.publicSignals.length}\n"
                )
                out.flush()
                Log.i(
                    TAG,
                    "[$name] iter=$iter witness=${"%.1f".format(witnessMs)}ms prove=${"%.1f".format(proveMs)}ms verify=$verifyOk"
                )
            } else {
                Log.i(
                    TAG,
                    "[$name] warmup witness=${"%.1f".format(witnessMs)}ms prove=${"%.1f".format(proveMs)}ms"
                )
            }
        }

        val wMean = witnessCalcs.average()
        val pMean = proves.average()
        val wSd = stddev(witnessCalcs, wMean)
        val pSd = stddev(proves, pMean)
        Log.i(
            TAG,
            "SUMMARY $name  witness=${"%.1f".format(wMean)}±${"%.1f".format(wSd)}ms  " +
                    "prove=${"%.1f".format(pMean)}±${"%.1f".format(pSd)}ms"
        )
    }

    private fun stddev(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        val s = values.sumOf { (it - mean) * (it - mean) }
        return sqrt(s / (values.size - 1))
    }
}
