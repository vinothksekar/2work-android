package com.twowork.core

/**
 * Client-side mirror of the server's Indian KYC validators (src/kyc-india.js):
 * PAN structure + holder type, Aadhaar Verhoeff checksum, GSTIN structure +
 * embedded-PAN + mod-36 checksum. The server stays authoritative; this just lets
 * the UI guide the user and gate the submit button.
 */
object KycValidators {
    private val PAN_RE = Regex("^[A-Z]{5}[0-9]{4}[A-Z]$")
    private val AADHAAR_RE = Regex("^[2-9][0-9]{11}$")
    private val GSTIN_RE = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]$")
    private val PAN_HOLDER_TYPES = setOf('A', 'B', 'C', 'F', 'G', 'H', 'J', 'L', 'P', 'T')
    private const val GSTN_CODEPOINTS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private val D = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
        intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
        intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
        intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
        intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
    )
    private val P = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
        intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
        intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
    )

    fun clean(value: String): String = value.replace(Regex("[\\s-]"), "").uppercase()

    private fun verhoeffValid(digits: String): Boolean {
        var c = 0
        val reversed = digits.reversed()
        for (i in reversed.indices) {
            c = D[c][P[i % 8][reversed[i] - '0']]
        }
        return c == 0
    }

    fun isValidPan(value: String): Boolean {
        val pan = clean(value)
        return PAN_RE.matches(pan) && pan[3] in PAN_HOLDER_TYPES
    }

    fun isValidAadhaar(value: String): Boolean {
        val aadhaar = clean(value)
        return AADHAAR_RE.matches(aadhaar) && verhoeffValid(aadhaar)
    }

    private fun gstinCheckDigit(first14: String): Char {
        var factor = 2
        var sum = 0
        val mod = GSTN_CODEPOINTS.length
        for (i in first14.indices.reversed()) {
            var addend = factor * GSTN_CODEPOINTS.indexOf(first14[i])
            factor = if (factor == 2) 1 else 2
            addend = addend / mod + addend % mod
            sum += addend
        }
        return GSTN_CODEPOINTS[(mod - (sum % mod)) % mod]
    }

    private fun validStateCode(code: String): Boolean {
        val n = code.toIntOrNull() ?: return false
        return n in 1..38 || n == 97 || n == 99
    }

    fun isValidGstin(value: String): Boolean {
        val gstin = clean(value)
        if (!GSTIN_RE.matches(gstin)) return false
        if (!validStateCode(gstin.substring(0, 2))) return false
        if (!isValidPan(gstin.substring(2, 12))) return false
        return gstinCheckDigit(gstin.substring(0, 14)) == gstin[14]
    }
}
