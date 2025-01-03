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

#### groth16Prove

Function takes path to .zkey file and witness file (as base64 encoded String) and returns proof and public signals.

Reads .zkey file directly from filesystem.


```Kotlin
import io.iden3.rapidsnark.*

// ...

val wtns: ByteArray = readFile("path/to/wtns")

val (proof, publicSignals) = groth16Prove("path/to/zkey", wtns)
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

#### groth16PublicBufferSize

Calculates public buffer size for specified zkey.

```Kotlin
import io.iden3.rapidsnark.*

// ...

val publicBufferSize = groth16PublicBufferSize("path/to/zkey")
```

### Public buffer size

`groth16Prove` has an optional `proofBufferSize`, `publicBufferSize` and `errorBufferSize`  parameters.
If `publicBufferSize` is too small it will be calculated automatically by library.

These parameters are used to set the size of the buffers used to store the proof, public signals and error.

If you have embedded circuit in the app, it is recommended to calculate the size of the public buffer once and reuse it.
To calculate the size of public buffer call `groth16PublicBufferSize`.

## Example

To run the example project, clone the repo, and run `app` module application.

## License

android-rapidsnark is part of the iden3 project copyright 2021 0KIMS association and published with LGPL-3 license. Please check the COPYING file for more details.
