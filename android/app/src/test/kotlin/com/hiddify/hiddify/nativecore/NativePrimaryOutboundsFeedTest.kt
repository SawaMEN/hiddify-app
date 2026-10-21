package com.hiddify.hiddify.nativecore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePrimaryOutboundsFeedTest {
    private val incompleteGrpc =
        "gRPC transport failure (HTTP status=200, grpc-status=null, grpc-message=null)"

    @Test fun hidesIsolatedGrpcTransportFailuresAfterASnapshot() {
        assertFalse(shouldReportOutboundsStreamError(true, 1, incompleteGrpc))
        assertFalse(shouldReportOutboundsStreamError(true, 2, incompleteGrpc))
        assertFalse(shouldReportOutboundsStreamError(true, 1, "Proxy updates stream closed"))
    }

    @Test fun reportsPersistentFailureAndNeverHidesInitialLoadErrors() {
        assertTrue(shouldReportOutboundsStreamError(true, 3, incompleteGrpc))
        assertTrue(shouldReportOutboundsStreamError(false, 1, incompleteGrpc))
        assertTrue(shouldReportOutboundsStreamError(false, 1, "Proxy updates stream closed"))
    }

    @Test fun immediatelyReportsNonTransientErrors() {
        assertTrue(shouldReportOutboundsStreamError(true, 1, "Permission denied"))
        assertTrue(shouldReportOutboundsStreamError(true, 1, "UNAUTHENTICATED"))
        assertTrue(shouldReportOutboundsStreamError(true, 1, "connection refused"))
    }
}
