package com.twowork.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KycValidatorsTest {

    @Test
    fun pan() {
        assertTrue(KycValidators.isValidPan("ABCPK1234L"))
        assertTrue(KycValidators.isValidPan("abcpk1234l"))
        assertFalse(KycValidators.isValidPan("ABCDE1234F")) // 4th char not a holder type
        assertFalse(KycValidators.isValidPan("ABCPK1234"))
    }

    @Test
    fun aadhaar() {
        assertTrue(KycValidators.isValidAadhaar("234567890124")) // valid Verhoeff (matches server vector)
        assertTrue(KycValidators.isValidAadhaar("2345 6789 0124"))
        assertFalse(KycValidators.isValidAadhaar("234567890123")) // wrong check digit
        assertFalse(KycValidators.isValidAadhaar("123456789012")) // leading 1
    }

    @Test
    fun gstin() {
        assertTrue(KycValidators.isValidGstin("27AAPFU0939F1ZV"))
        assertFalse(KycValidators.isValidGstin("27AAPFU0939F1ZZ")) // wrong checksum
        assertFalse(KycValidators.isValidGstin("00AAPFU0939F1ZV")) // bad state code
    }
}
