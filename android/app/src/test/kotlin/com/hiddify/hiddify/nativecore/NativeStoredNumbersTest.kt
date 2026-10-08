package com.hiddify.hiddify.nativecore

import org.junit.Assert.*
import org.junit.Test

class NativeStoredNumbersTest {
    @Test fun portsNeverWrapOrRoundIntoOtherValidPorts() {
        for (invalid in listOf(4_294_979_630L, 12334.5, Double.NaN, Double.POSITIVE_INFINITY, "12334.5", -1, 65536)) {
            assertNull(NativeStoredNumbers.int(invalid, 0..65535))
        }
        for (valid in listOf(12334, 12334L, 12334.0, "12334")) {
            assertEquals(12334, NativeStoredNumbers.int(valid, 0..65535))
        }
    }

    @Test fun disabledLegacyPortsRemainZeroAndUseExplicitEnableFlags() {
        assertEquals(0, NativeStoredNumbers.int(0L, 0..65535))
        assertNull(NativeStoredNumbers.int(0L, 1..65535))
    }
}
