package org.localverify.experiment
import org.localverify.engine.*
typealias Token = Cancellation
typealias Definition = Indicator
object Adapter {
    private val engine=Engine()
    fun parse(data:ByteArray,cancel:Token) = engine.parseBundle(data,cancel)
    fun scan(data:ByteArray,indicators:List<Definition>,cancel:Token) = engine.scanRecord(data,"fixture.txt",indicators,cancel)
}
