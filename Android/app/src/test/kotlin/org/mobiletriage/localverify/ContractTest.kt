package org.mobiletriage.localverify
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONArray
import org.mobiletriage.localverify.core.*
class ContractTest {
    private fun rows(name: String): JSONArray = javaClass.classLoader.getResourceAsStream("fixtures/$name.json")!!.bufferedReader().use { JSONArray(it.readText()) }
    @Test fun stix() {
        val rows = rows("stix")
        for (i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            val data=(if(row.has("raw")) row.getString("raw") else row.getJSONObject("bundle").toString()).toByteArray()
            val error=row.optJSONObject("legacy")?.optJSONObject("android")?.getBoolean("error") ?: row.getBoolean("error")
            val result=runCatching { IndicatorParser.parse(data) }
            assertEquals(row.getString("id"), error, result.isFailure)
            if(!error) { assertEquals(row.getString("id"),row.getInt("supported"),result.getOrThrow().indicators.size); assertEquals(row.getInt("unsupported"),result.getOrThrow().unsupported.size) }
        }
    }
    @Test fun budgets() {
        val rows=rows("budgets")
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            val text=row.optString("prefix", "")+row.getString("unit").repeat(row.getInt("repeat"))+row.optString("suffix", "")
            val error=row.optJSONObject("legacy")?.optJSONObject("android")?.getBoolean("error") ?: row.getBoolean("error")
            val result=runCatching { TriageAnalyzer.scanText(text,"fixture.txt",IndicatorSet.demo.indicators,{}) }
            assertEquals(row.getString("id"),error,result.isFailure)
        }
    }
}
