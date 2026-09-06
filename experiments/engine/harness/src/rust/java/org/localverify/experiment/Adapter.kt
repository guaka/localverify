package org.localverify.experiment
import uniffi.triage_experiment.*
typealias Token = Cancellation
typealias Definition = Indicator
object Adapter {
    fun parse(data:ByteArray,cancel:Token) = parseBundle(data,cancel)
    fun scan(data:ByteArray,indicators:List<Definition>,cancel:Token) = scanRecord(data,"fixture.txt",indicators,cancel)
}
