# rapidsnark

---

This library is Android Kotlin wrapper for the [Rapidsnark](https://github.com/iden3/rapidsnark). It enables the
generation of proofs for specified circuits within an Android environment.

## Platform Support

**Android**: Compatible with any Android device with 64 bit architecture.

## Requirements

- Android 7.0 (API level 24) or higher.

## Installation

// TODO: Add instructions for installation

## Usage

#### groth16ProveWithZKeyFilePath

Function takes path to .zkey file and witness file (as base64 encoded String) and returns proof and public signals.

Reads .zkey file directly from filesystem.


```Kotlin
import io.iden3.rapidsnark.*

// ...

val wtns: ByteArray = readFile("path/to/wtns")

val (proof, publicSignals) = groth16ProveWithZKeyFilePath("path/to/zkey", wtns)
```

#### groth16Verify

Verifies proof and public signals against verification key.

```Kotlin
import io.iden3.rapidsnark.*

// ...

val zkey: ByteArray = readFile("path/to/zkey")
val wtns: ByteArray = readFile("path/to/zkey")
val verificationKey: ByteArray = readFile("path/to/zkey")

val (proof, publicSignals) = groth16Prove(zkey, wtns)

val proofValid = groth16Verify(proof, publicSignals, verificationKey)
```

#### groth16Prove

Function that takes zkey and witness files encoded as base64.

`proof` and `publicSignals` are base64 encoded strings.

>Large circuits might cause OOM. Use with caution.

```Kotlin
import io.iden3.rapidsnark.*

// ...

val zkey: ByteArray = readFile("path/to/zkey")
val wtns: ByteArray = readFile("path/to/zkey")

val (proof, publicSignals) =  groth16Prove(zkey, wtns)
```
#### groth16PublicSizeForZkeyFile

Calculates public buffer size for specified zkey.

```Kotlin
import io.iden3.rapidsnark.*

// ...

val publicBufferSize = groth16PublicSizeForZkeyFile("path/to/zkey")
```

### Public buffer size

Both `groth16Prove` and `groth16ProveWithZKeyFilePath` has an optional `proofBufferSize`, `publicBufferSize` and `errorBufferSize`  parameters.
If `publicBufferSize` is too small it will be calculated automatically by library.

These parameters are used to set the size of the buffers used to store the proof, public signals and error.

If you have embedded circuit in the app, it is recommended to calculate the size of the public buffer once and reuse it.
To calculate the size of public buffer call `groth16PublicSizeForZkeyFile`.

## Example

To run the example project, clone the repo, and run `app` module application.

## License

android-rapidsnark is part of the iden3 project 0KIMS association. Please check the [COPYING](./COPYING) file for more details.
