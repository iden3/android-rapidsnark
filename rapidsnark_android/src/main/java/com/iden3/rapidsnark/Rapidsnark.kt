package com.iden3.rapidsnark

import java.nio.charset.StandardCharsets


const val DEFAULT_PROOF_BUFFER_SIZE = 1024
const val DEFAULT_ERROR_BUFFER_SIZE = 256

// Prover status codes
private const val PROVER_OK = 0x0
private const val PROVER_ERROR = 0x1
private const val PROVER_ERROR_SHORT_BUFFER = 0x2
private const val PROVER_INVALID_WITNESS_LENGTH = 0x3

// Verifier status codes
private const val VERIFIER_VALID_PROOF = 0x0
private const val VERIFIER_INVALID_PROOF = 0x1
private const val VERIFIER_ERROR = 0x2


private val rapidsnarkJNI = RapidsnarkJniBridge()

fun groth16Prove(
    zkey: ByteArray,
    witness: ByteArray,
    proofBufferSize: Int = DEFAULT_PROOF_BUFFER_SIZE,
    publicBufferSize: Int? = null,
    errorBufferSize: Int = DEFAULT_ERROR_BUFFER_SIZE
): ProveResponse {
    val publicBufferSizeNonNull =
        publicBufferSize ?: groth16PublicSizeForZkeyBuf(zkey, errorBufferSize)

    // Create buffers to get results
    val proofBuffer = ByteArray(proofBufferSize)
    val publicBuffer = ByteArray(publicBufferSizeNonNull)
    val errorBuffer = ByteArray(errorBufferSize)

    val statusCode = rapidsnarkJNI.groth16Prove(
        zkey, zkey.size.toLong(),
        witness, witness.size.toLong(),
        proofBuffer, longArrayOf(proofBufferSize.toLong()),
        publicBuffer, longArrayOf(publicBufferSizeNonNull.toLong()),
        errorBuffer, errorBufferSize.toLong()
    )

    if (statusCode == PROVER_OK) {
        // Convert byte arrays to strings
        val proof = String(proofBuffer, StandardCharsets.UTF_8).trim { it <= ' ' }
        val public = String(publicBuffer, StandardCharsets.UTF_8).trim { it <= ' ' }

        return ProveResponse(proof, public)
    }

    val error = String(errorBuffer, StandardCharsets.UTF_8)
    when (statusCode) {
        PROVER_ERROR -> throw RapidsnarkProverError(error)
        PROVER_ERROR_SHORT_BUFFER -> throw RapidsnarkProverError.ShortBuffer(error)
        PROVER_INVALID_WITNESS_LENGTH -> throw RapidsnarkProverError.InvalidWitnessLength(error)
        else -> throw RapidsnarkUnknownStatusError()
    }
}

fun groth16ProveWithZKeyFilePath(
    zkeyPath: String,
    witness: ByteArray,
    proofBufferSize: Int = DEFAULT_PROOF_BUFFER_SIZE,
    publicBufferSize: Int? = null,
    errorBufferSize: Int = DEFAULT_ERROR_BUFFER_SIZE
): ProveResponse {
    val publicBufferSizeNonNull =
        publicBufferSize ?: groth16PublicSizeForZkeyFile(zkeyPath, errorBufferSize)

    // Create buffers to get results
    val proofBuffer = ByteArray(proofBufferSize)
    val publicBuffer = ByteArray(publicBufferSizeNonNull)
    val errorBuffer = ByteArray(errorBufferSize)

    val statusCode = rapidsnarkJNI.groth16ProveWithZKeyFilePath(
        zkeyPath,
        witness, witness.size.toLong(),
        proofBuffer, longArrayOf(proofBufferSize.toLong()),
        publicBuffer, longArrayOf(publicBufferSizeNonNull.toLong()),
        errorBuffer, errorBufferSize.toLong()
    )

    if (statusCode == PROVER_OK) {
        // Convert byte arrays to strings
        val proof = String(proofBuffer, StandardCharsets.UTF_8).trim { it <= ' ' }
        val public = String(publicBuffer, StandardCharsets.UTF_8).trim { it <= ' ' }

        return ProveResponse(proof, public)
    }

    val error = String(errorBuffer, StandardCharsets.UTF_8)
    when (statusCode) {
        PROVER_ERROR -> throw RapidsnarkProverError(error)
        PROVER_ERROR_SHORT_BUFFER -> throw RapidsnarkProverError.ShortBuffer(error)
        PROVER_INVALID_WITNESS_LENGTH -> throw RapidsnarkProverError.InvalidWitnessLength(error)
        else -> throw RapidsnarkUnknownStatusError()
    }
}

fun groth16Verify(
    proof: String,
    inputs: String,
    verificationKey: String,
    errorBufferSize: Int = DEFAULT_ERROR_BUFFER_SIZE
): Boolean {
    val errorBuffer = ByteArray(errorBufferSize)

    val result = rapidsnarkJNI.groth16Verify(
        proof,
        inputs,
        verificationKey,
        errorBuffer,
        errorBufferSize.toLong()
    )

    if (result == VERIFIER_VALID_PROOF) {
        return true
    }

    val error = String(errorBuffer, StandardCharsets.UTF_8)
    when (result) {
        VERIFIER_INVALID_PROOF -> throw RapidsnarkVerifierError.InvalidProof(error)
        VERIFIER_ERROR -> throw RapidsnarkVerifierError(error)
        else -> throw RapidsnarkUnknownStatusError()
    }
}

fun groth16PublicSizeForZkeyBuf(
    zkey: ByteArray,
    errorBufferSize: Int = DEFAULT_ERROR_BUFFER_SIZE
): Int {
    val errorBuffer = ByteArray(errorBufferSize)

    val publicBufferSize = rapidsnarkJNI.groth16PublicSizeForZkeyBuf(
        zkey,
        zkey.size.toLong(),
        errorBuffer,
        errorBufferSize.toLong(),
    )
    val error = String(errorBuffer, StandardCharsets.UTF_8)

    if (error.isEmpty()) {
        return publicBufferSize.toInt()
    } else {
        throw RapidsnarkProverError(error)
    }
}

fun groth16PublicSizeForZkeyFile(
    zkeyPath: String,
    errorBufferSize: Int = DEFAULT_ERROR_BUFFER_SIZE
): Int {
    val errorBuffer = ByteArray(errorBufferSize)

    val publicBufferSize = rapidsnarkJNI.groth16PublicSizeForZkeyFile(
        zkeyPath,
        errorBuffer,
        errorBufferSize.toLong(),
    )

    val error = String(errorBuffer, StandardCharsets.UTF_8)

    if (error.isEmpty()) {
        return publicBufferSize.toInt()
    } else {
        throw RapidsnarkProverError(error)
    }
}

abstract class RapidsnarkError(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause)

open class RapidsnarkProverError(message: String? = null, cause: Throwable? = null) :
    RapidsnarkError(message, cause) {
    class ShortBuffer(message: String) : RapidsnarkProverError(message)
    class InvalidWitnessLength(message: String) : RapidsnarkProverError(message)
}

open class RapidsnarkVerifierError(message: String? = null, cause: Throwable? = null) :
    RapidsnarkError(message, cause) {
    class InvalidProof(message: String) : RapidsnarkVerifierError(message)
}

class RapidsnarkUnknownStatusError(message: String? = null, cause: Throwable? = null) :
    RapidsnarkError(message, cause)

data class ProveResponse(
    val proof: String,
    val publicSignals: String
)


private class RapidsnarkJniBridge {
    companion object {
        init {
            System.loadLibrary("rapidsnark_module")
        }
    }

    external fun groth16Prove(
        zkeyBuffer: ByteArray, zkeySize: Long,
        wtnsBuffer: ByteArray, wtnsSize: Long,
        proofBuffer: ByteArray, proofSize: LongArray,
        publicBuffer: ByteArray, publicSize: LongArray,
        errorMsg: ByteArray, errorMsgMaxSize: Long
    ): Int

    external fun groth16ProveWithZKeyFilePath(
        zkeyPath: String,
        wtnsBuffer: ByteArray, wtnsSize: Long,
        proofBuffer: ByteArray, proofSize: LongArray,
        publicBuffer: ByteArray, publicSize: LongArray,
        errorMsg: ByteArray, errorMsgMaxSize: Long
    ): Int

    external fun groth16Verify(
        proof: String,
        inputs: String,
        verificationKey: String,
        errorMsg: ByteArray,
        errorMsgMaxSize: Long
    ): Int

    external fun groth16PublicSizeForZkeyBuf(
        zkeyBuffer: ByteArray,
        zkeySize: Long,
        errorMsg: ByteArray,
        errorMsgMaxSize: Long
    ): Long

    external fun groth16PublicSizeForZkeyFile(
        zkeyPath: String,
        errorMsg: ByteArray,
        errorMsgMaxSize: Long
    ): Long
}

