package com.bottlepost.app.data.remote

import com.google.firebase.functions.FirebaseFunctionsException.Code
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

// FirebaseFunctionsException.Code initializes with Android classes, hence Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ErrorCodeTest {
    @Test
    fun serverCodesWinOverStatus() {
        assertEquals("no_recipient_available", errorCode(Code.UNAVAILABLE, "no_recipient_available", null))
        assertEquals("service_paused", errorCode(Code.UNAVAILABLE, "service_paused", null))
        assertEquals("wait_for_reply", errorCode(Code.FAILED_PRECONDITION, "wait_for_reply", null))
    }

    @Test
    fun sdkErrorsWhenOfflineAreNetwork() {
        assertEquals("network", errorCode(Code.UNAVAILABLE, "UNAVAILABLE", null))
        assertEquals("network", errorCode(Code.DEADLINE_EXCEEDED, null, null))
        assertEquals("network", errorCode(Code.INTERNAL, "INTERNAL", IOException("reset")))
    }

    @Test
    fun otherSdkErrorsKeepTheirName() {
        assertEquals("INTERNAL", errorCode(Code.INTERNAL, "INTERNAL", null))
        assertEquals("unauthenticated", errorCode(Code.UNAUTHENTICATED, null, null))
    }
}
