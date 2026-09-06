package org.localverify.record
import org.localverify.record.checks.ContractChecks
import kotlin.test.Test
class ContractTest {
    @Test fun canonicalContract() {
        println("SharedEngineMeasurements " + ContractChecks.run { name -> javaClass.classLoader.getResourceAsStream(name)!!.use { it.readBytes() } })
    }
}
