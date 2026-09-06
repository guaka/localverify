package org.localverify.experiment
import android.os.Debug
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
class EngineTest {
    private fun rows(name:String)=InstrumentationRegistry.getInstrumentation().context.assets.open("$name.json").bufferedReader().use {JSONArray(it.readText())}
    @Test fun contractAndMeasurements() {
        var checked=0
        val matching=rows("matching")
        for(i in 0 until matching.length()) {
            val r=matching.getJSONObject(i);val id=r.getString("id")
            val item=JSONObject().put("type","indicator").put("id","test-indicator").put("pattern","[${r.getString("indicatorKind")} = '${r.getString("indicatorValue")}']")
            val bundle=JSONObject().put("type","bundle").put("objects",JSONArray().put(item))
            val parsed=Adapter.parse(bundle.toString().toByteArray(),Token())
            assertNull(id,parsed.error);assertEquals(id,1,parsed.indicators.size)
            val result=Adapter.scan(r.getString("text").toByteArray(),parsed.indicators,Token())
            assertTrue(id,result.findings.all {it.rule=="test-indicator"&&it.value==r.getString("indicatorValue")&&it.source=="fixture.txt"})
            assertEquals(id,r.getInt("expected"),result.findings.size)
            assertTrue(id,result.coverageGaps.isEmpty());assertFalse(id,result.cancelled)
            if(!r.isNull("matchType"))assertTrue(id,result.findings.all {it.matchType==r.getString("matchType")})
            if(r.has("timestamp"))assertEquals(id,r.getString("timestamp"),result.findings.first().timestamp)
            checked++
        }
        val stix=rows("stix")
        for(i in 0 until stix.length()) {
            val r=stix.getJSONObject(i);val data=(if(r.has("raw"))r.getString("raw") else r.getJSONObject("bundle").toString()).toByteArray()
            val result=Adapter.parse(data,Token())
            assertEquals(r.getString("id"),r.getBoolean("error"),result.error!=null)
            assertEquals(r.getInt("supported"),result.indicators.size);assertEquals(r.getInt("unsupported"),result.unsupported.size);checked++
        }
        val indicator=Definition("test","domain-name:value","triage-test.invalid")
        val budgets=rows("budgets")
        for(i in 0 until budgets.length()) {
            val r=budgets.getJSONObject(i);val text=r.optString("prefix","")+r.getString("unit").repeat(r.getInt("repeat"))+r.optString("suffix","")
            assertEquals(r.getString("id"),r.getBoolean("error"),Adapter.scan(text.toByteArray(),listOf(indicator),Token()).coverageGaps.isNotEmpty());checked++
        }
        assertNotNull(Adapter.parse(byteArrayOf(-1),Token()).error)
        assertTrue(Adapter.scan(byteArrayOf(-1),listOf(indicator),Token()).coverageGaps.isNotEmpty())
        assertNotNull(Adapter.parse(ByteArray(5*1024*1024+1){32},Token()).error)
        assertTrue(Adapter.scan(ByteArray(16*1024*1024+1){32},listOf(indicator),Token()).coverageGaps.isNotEmpty())
        val c=Token();c.cancel();assertTrue(Adapter.scan("benign".toByteArray(),listOf(indicator),c).cancelled)
        assertEquals("cancelled",Adapter.parse("{}".toByteArray(),c).error)
        val config=InstrumentationRegistry.getInstrumentation().context.assets.open("benchmark.json").bufferedReader().use {JSONObject(it.readText())}
        val cancelConfig=config.getJSONObject("cancel");val scanConfig=config.getJSONObject("scan")
        val workload=cancelConfig.getString("unit").repeat(cancelConfig.getInt("repeat")).toByteArray()
        val many=(0 until cancelConfig.getInt("definitions")).map {Definition("$it","process:name","missing-$it")}
        val live=Token();val done=CountDownLatch(1);val cancelled=AtomicBoolean(false)
        thread {try {cancelled.set(Adapter.scan(workload,many,live).cancelled)} finally {done.countDown()}}
        val waiting=System.nanoTime()
        while(live.progressUnits().toLong()==0L && System.nanoTime()-waiting<5_000_000_000L) Thread.sleep(0,100000)
        assertTrue("scan started before cancellation",live.progressUnits().toLong()>0)
        val signalled=System.nanoTime();live.cancel()
        assertTrue(done.await(5,TimeUnit.SECONDS));assertTrue(cancelled.get())
        val cancelMs=(System.nanoTime()-signalled)/1e6
        val bench=scanConfig.getString("unit").repeat(scanConfig.getInt("repeat")).toByteArray()
        Adapter.scan(bench,listOf(indicator),Token())
        val before=Debug.MemoryInfo();Debug.getMemoryInfo(before)
        val times=JSONArray()
        repeat(scanConfig.getInt("iterations")) {val t=System.nanoTime();val result=Adapter.scan(bench,listOf(indicator),Token());assertEquals(scanConfig.getInt("expectedFindings"),result.findings.size);assertTrue(result.coverageGaps.isEmpty());times.put((System.nanoTime()-t)/1e6)}
        val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory)
        val result=JSONObject().put("platform","android-emulator").put("cases",checked+7).put("scanMs",times).put("inputBytes",bench.size).put("pssBeforeBenchmarkKiB",before.totalPss).put("pssAfterBenchmarkKiB",memory.totalPss).put("cancelMs",cancelMs)
        Log.i("EngineExperiment",result.toString())
        InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply {putString("engineMeasurements",result.toString())})
    }
}
