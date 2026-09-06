@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package org.localverify.engine

import kotlin.concurrent.atomics.AtomicInt
import kotlinx.serialization.json.*

// Experimental record slice only: no platform file, network, archive, or case APIs.
class Cancellation {
    private val state = AtomicInt(0)
    private val progress = AtomicInt(0)
    fun progressUnits(): Int = progress.load()
    internal fun advance() { progress.fetchAndAdd(1) }
    fun cancel() { state.store(1) }
    fun isCancelled(): Boolean = state.load() != 0
}
data class Indicator(val id: String, val kind: String, val value: String)
data class Parsed(val indicators: List<Indicator>, val unsupported: List<String>, val error: String?)
data class Finding(val rule: String, val value: String, val source: String, val record: String, val matchType: String, val timestamp: String?, val excerpt: String)
data class ScanResult(val findings: List<Finding>, val coverageGaps: List<String>, val cancelled: Boolean, val visitedUnits: Long)
private class Stop(val reason: String): Exception(reason)
private fun checkCancel(c: Cancellation) { if(c.isCancelled()) throw Stop("cancelled") }
private val kinds = setOf("domain-name:value","url:value","process:name","file:path","file:name")
private val aliases = mapOf("procname" to "process:name", "process" to "process:name", "processname" to "process:name", "process_name" to "process:name", "app_name" to "process:name", "procpath" to "file:path", "path" to "file:path", "executablepath" to "file:path", "filename" to "file:name", "name" to "file:name", "domain" to "domain-name:value", "hostname" to "domain-name:value", "host" to "domain-name:value", "url" to "url:value", "uri" to "url:value")
private fun byteSize(s: String) = s.encodeToByteArray().size
private fun JsonObject.string(k:String): String? = (get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun budget(s: String,c:Cancellation) {
    var depth=0; var tokens=0; var quoted=false; var escaped=false
    s.forEachIndexed { i,b ->
        if(i%4096==0) checkCancel(c)
        if(quoted) { if(escaped) escaped=false else if(b=='\\') escaped=true else if(b=='"') quoted=false }
        else { when(b) { '"' -> {quoted=true;tokens++}; '[','{' -> {depth++;tokens++}; ']','}' -> depth=maxOf(0,depth-1); ',' -> tokens++ }
            if(depth>64||tokens>200_000) throw Stop("JSON complexity limit reached")
        }
    }
}
private fun boundary(c:Char,domain:Boolean):Boolean = c in 'a'..'z'||c in 'A'..'Z'||c in '0'..'9'||c in if(domain) "_.-" else "_./:%?=&-"
private fun asciiLower(s:String)=buildString(s.length) { s.forEach { append(if(it in 'A'..'Z') it+32 else it) } }
private fun matched(line:String,value:String,domain:Boolean):Boolean {
    val l=if(domain) asciiLower(line) else line; val v=if(domain) asciiLower(value) else value
    var start=0
    while(start<=l.length-v.length) {
        val i=l.indexOf(v,start); if(i<0) return false
        if((i==0||!boundary(l[i-1],domain))&&(i+v.length==l.length||!boundary(l[i+v.length],domain))) return true
        start=i+v.length
    }
    return false
}
private fun normalize(v:String,k:String)=if(k=="domain-name:value") v.trim('.').lowercase() else v
private data class Record(val path:String,val kind:String,val value:String,val time:String?)
class Engine {
    fun parseBundle(data:ByteArray,cancel:Cancellation):Parsed {
        val found=mutableListOf<Indicator>(); val skipped=mutableListOf<String>()
        try {
            if(data.size>5*1024*1024) throw Stop("Indicator byte limit")
            val s=try { data.decodeToString(throwOnInvalidSequence=true) } catch(_:CharacterCodingException) { throw Stop("Invalid UTF-8") }
            budget(s,cancel)
            val root=try { Json.parseToJsonElement(s) as? JsonObject } catch(_:IllegalArgumentException) { null }
            if(root?.string("type")!="bundle") throw Stop("Expected STIX2 bundle")
            val objects=root["objects"] as? JsonArray ?: throw Stop("Expected objects array")
            val regex=Regex("^\\s*\\[\\s*([a-z-]+:[a-z]+)\\s*=\\s*'([^'\\\\]+)'\\s*\\]\\s*$")
            for(value in objects) {
                checkCancel(cancel); val item=value as? JsonObject ?: continue
                if(item.string("type")!="indicator") continue
                val id=item.string("id")?:"unnamed";val pattern=item.string("pattern")?:""
                if(byteSize(id)>1024||byteSize(pattern)>8192) throw Stop("Indicator metadata limit")
                val m=regex.matchEntire(pattern)
                if(item["revoked"]==JsonPrimitive(true)||item.containsKey("valid_until")||(item.containsKey("pattern_type")&&item.string("pattern_type")!="stix")||m==null) {skipped.add(id);continue}
                val k=m.groupValues[1];val v=m.groupValues[2]
                if(k !in kinds||v.isBlank()||v.codePointCount()>2048||found.size>=2000) {skipped.add(id);continue}
                found.add(Indicator(id,k,v))
            }
            return Parsed(found,skipped,null)
        } catch(e:Stop) {return Parsed(emptyList(),skipped,e.reason)}
    }
    fun scanRecord(data:ByteArray,source:String,indicators:List<Indicator>,cancel:Cancellation):ScanResult {
        val findings=mutableListOf<Finding>(); val gaps=mutableListOf<String>(); var visited=0L
        try {
            checkCancel(cancel)
            if(data.size>16*1024*1024) throw Stop("Text byte limit")
            if(indicators.size>10_000||indicators.any {it.value.isEmpty()||byteSize(it.value)>8192||byteSize(it.id)>1024||it.kind !in kinds}) throw Stop("Indicator limit")
            val text=try {data.decodeToString(throwOnInvalidSequence=true)} catch(_:CharacterCodingException) {throw Stop("Invalid UTF-8")}
            var lineLength=0;var lineCount=1
            data.forEachIndexed {i,b-> if(i%4096==0) checkCancel(cancel);if(b==10.toByte()||b==13.toByte()){lineCount++;lineLength=0}else lineLength++;if(lineCount>500_000||lineLength>1024*1024)throw Stop("Text line limit")}
            val rows=mutableListOf<Record>();var nodes=0
            fun collect(v:JsonElement,path:String,time:String?) {
                checkCancel(cancel);nodes++
                if(nodes>100_000||byteSize(path)>4096) throw Stop("Structured record limit")
                when(v) {
                    is JsonObject -> {
                        val t=(v.string("timestamp")?:v.string("captureTime")?:time)?.takeCodePoints(256)
                        for(k in v.keys.sorted()) {
                            val child=v.getValue(k);val p="$path.$k"
                            if(byteSize(p)>4096) throw Stop("Structured path limit")
                            val str=(child as? JsonPrimitive)?.takeIf {it.isString}?.content
                            if(str!=null) {val kind=aliases[k.lowercase()];if(kind!=null&&byteSize(str)<=8192) rows.add(Record(p,kind,str,t))}
                            else collect(child,p,t)
                        }
                    }
                    is JsonArray -> v.forEachIndexed {i,child->collect(child,"$path[$i]",time)}
                    else -> Unit
                }
            }
            fun decode(s:String,path:String):Boolean {
                if(!s.trimStart().startsWith("{")&&!s.trimStart().startsWith("[")) return false
                budget(s,cancel)
                val json=try {Json.parseToJsonElement(s)} catch(_:IllegalArgumentException) {return false}
                collect(json,path,null);return true
            }
            try {if(!decode(text,"$")) {val n=text.indexOf('\n');if(n>=0){decode(text.substring(0,n),"\$header");decode(text.substring(n+1),"\$body")}}}
            catch(e:Stop) {if(e.reason=="cancelled")throw e;rows.clear();gaps.add(e.reason)}
            var work=0L
            for(indicator in indicators) {
                checkCancel(cancel);visited++;cancel.advance()
                fun append(record:String,excerpt:String,time:String?,type:String) {
                    checkCancel(cancel);if(findings.size>=10_000)throw Stop("Finding limit")
                    findings.add(Finding(indicator.id,indicator.value,source,record,type,time,excerpt.takeCodePoints(600)))
                }
                val selected=rows.filter {it.kind==indicator.kind&&normalize(it.value,it.kind)==normalize(indicator.value,it.kind)}
                if(selected.isNotEmpty()) selected.forEach {append(it.path,it.value,it.time,"structured")}
                else text.splitToSequence('\n').forEachIndexed {i,line->checkCancel(cancel);work+=byteSize(line)+1;if(work>128*1024*1024)throw Stop("Text work limit");if(matched(line,indicator.value,indicator.kind=="domain-name:value"))append("line ${i+1}",line,null,"raw-text")}
            }
            return ScanResult(findings,gaps,false,visited)
        } catch(e:Stop) {if(e.reason!="cancelled")gaps.add(e.reason);return ScanResult(findings,gaps,e.reason=="cancelled",visited)}
    }
}
private fun String.codePointCount():Int = indices.count { !this[it].isLowSurrogate() || it==0 || !this[it-1].isHighSurrogate() }
private fun String.takeCodePoints(limit:Int):String {
    var i=0;var n=0
    while(i<length&&n<limit){if(this[i].isHighSurrogate()&&i+1<length&&this[i+1].isLowSurrogate())i++;i++;n++}
    return substring(0,i)
}
