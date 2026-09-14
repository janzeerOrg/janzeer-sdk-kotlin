package org.janzeer.sdk.crypto

import java.security.SecureRandom

/** BIP39 English mnemonics (standard wordlist + checksum); only the seed derivation is Janzeer-specific. */
public object Mnemonic {
    /** The 2048 English words. */
    @JvmStatic public val wordlist: List<String> by lazy {
        Mnemonic::class.java.getResourceAsStream("/org/janzeer/sdk/bip39-english.txt")!!.bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
            .also { require(it.size == 2048) { "wordlist must be 2048 words" } }
    }
    private val index: Map<String, Int> by lazy { wordlist.withIndex().associate { it.value to it.index } }

    /** A fresh mnemonic: 12 words (128-bit, default), 15 (160), 18 (192), 21 (224) or 24 (256). */
    @JvmStatic @JvmOverloads public fun generate(strength: Int = 128): String {
        require(strength in listOf(128, 160, 192, 224, 256)) { "strength must be 128..256 in steps of 32" }
        return fromEntropy(ByteArray(strength / 8).also { SecureRandom().nextBytes(it) })
    }

    /** Normalize whitespace and case so equivalent phrases derive the same seed. */
    @JvmStatic public fun normalize(mnemonic: String): String = mnemonic.trim().lowercase().split(Regex("\\s+")).joinToString(" ")

    /** Wordlist membership + checksum. Client-side UX only — the node never sees a mnemonic. */
    @JvmStatic public fun validate(mnemonic: String): Boolean = try {
        toEntropy(mnemonic); true
    } catch (e: IllegalArgumentException) {
        false
    }

    /** Janzeer seed (PBKDF2-HMAC-SHA512, salt `@_Janzeer_Blockchain_@` + passphrase) — NOT the BIP39 standard seed. */
    @JvmStatic @JvmOverloads public fun toSeed(mnemonic: String, passphrase: String = ""): ByteArray = Hd.mnemonicToSeed(normalize(mnemonic), passphrase)

    /** Entropy bytes → mnemonic. */
    @JvmStatic public fun fromEntropy(entropy: ByteArray): String {
        require(entropy.size in listOf(16, 20, 24, 28, 32)) { "entropy must be 16..32 bytes" }
        val checksumBits = entropy.size * 8 / 32
        val checksum = Hashes.sha256(entropy)
        val bits = StringBuilder()
        for (b in entropy) bits.append(((b.toInt() and 0xff) or 0x100).toString(2).substring(1))
        for (i in 0 until checksumBits) bits.append((checksum[i / 8].toInt() shr (7 - i % 8)) and 1)
        return (0 until bits.length step 11).joinToString(" ") { wordlist[bits.substring(it, it + 11).toInt(2)] }
    }

    /** Mnemonic → entropy bytes (throws [IllegalArgumentException] on an unknown word or bad checksum). */
    @JvmStatic public fun toEntropy(mnemonic: String): ByteArray {
        val words = normalize(mnemonic).split(' ')
        require(words.size in listOf(12, 15, 18, 21, 24)) { "mnemonic must have 12..24 words" }
        val bits = StringBuilder()
        for (w in words) {
            val i = index[w] ?: throw IllegalArgumentException("unknown word: $w")
            bits.append(i.toString(2).padStart(11, '0'))
        }
        val entropyBits = words.size * 11 * 32 / 33
        val entropy = ByteArray(entropyBits / 8) { bits.substring(it * 8, it * 8 + 8).toInt(2).toByte() }
        val checksum = Hashes.sha256(entropy)
        for (i in 0 until words.size * 11 - entropyBits) {
            require(bits[entropyBits + i] == ('0' + ((checksum[i / 8].toInt() shr (7 - i % 8)) and 1))) { "bad checksum" }
        }
        return entropy
    }
}
