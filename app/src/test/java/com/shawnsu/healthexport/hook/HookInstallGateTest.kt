package com.shawnsu.healthexport.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookInstallGateTest {

    @Test
    fun installsEachHookOncePerProcess() {
        val gate = HookInstallGate()
        assertTrue(gate.claimAttach())
        assertFalse(gate.claimAttach())
        assertTrue(gate.claimRuntime())
        assertFalse(gate.claimRuntime())
        assertTrue(gate.claimForeground())
        assertFalse(gate.claimForeground())
    }

    @Test
    fun dispatchesOnlyWhenRequestChanges() {
        val gate = HookInstallGate()
        assertTrue(gate.claimRequest("request-a"))
        assertFalse(gate.claimRequest("request-a"))
        assertTrue(gate.claimRequest("request-b"))
        assertFalse(gate.claimRequest("request-b"))
        assertTrue(gate.claimRequest("request-a"))
    }
}
