package org.localverify.shared
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import org.localverify.record.checks.ContractChecks
class ContractTest {
    @Test fun canonicalContract() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val result = ContractChecks.run { name -> instrumentation.context.assets.open(name).use { it.readBytes() } }
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("sharedEngineMeasurements", result) })
    }
}
