package tr.gundem
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.Test
import org.junit.Assert.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.MessageDigest

class ModelTransferTest {
    private val data="GGUF-real-test-payload".toByteArray()
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun spec()=ModelSpec("https://example.org/model.gguf",data.size.toLong(),sha(data),"Test")
    @Test fun unavailableModelNeverStartsANetworkTransfer()=runTest {
        val file=Files.createTempFile("gundem-unavailable",".part").toFile()
        try {
            val unavailable=ModelSpec("",0,"","Unavailable",availabilityReason="Mobil destek yok")
            try {
                ModelTransfer.download(unavailable,file,{}) { error("Network must not open") }
                fail("Unavailable model accepted")
            } catch(e:IllegalArgumentException) { assertEquals("Mobil destek yok",e.message);assertEquals(0L,file.length()) }
        } finally { file.delete() }
    }
    private class Reply(url:URL,val code:Int,val body:ByteArray,val headers:Map<String,String> = emptyMap()):HttpURLConnection(url) {
        override fun connect() {};override fun disconnect() {};override fun usingProxy()=false
        override fun getResponseCode()=code
        override fun getInputStream()=ByteArrayInputStream(body)
        override fun getHeaderField(name:String)=headers[name]
    }
    @Test fun downloadsAndVerifiesBytes()=runTest {
        val file=Files.createTempFile("gundem-model",".part").toFile()
        try { val counts=mutableListOf<Long>();ModelTransfer.download(spec(),file,{counts+=it}) { Reply(it,200,data) };assertArrayEquals(data,file.readBytes());assertEquals(data.size.toLong(),counts.last()) } finally {file.delete()}
    }
    @Test fun resumesOnlyWithValidRange()=runTest {
        val file=Files.createTempFile("gundem-model",".part").toFile();file.writeBytes(data.take(4).toByteArray())
        try { ModelTransfer.download(spec(),file,{}) { url -> Reply(url,206,data.drop(4).toByteArray(),mapOf("Content-Range" to "bytes 4-${data.size-1}/${data.size}")) };assertArrayEquals(data,file.readBytes()) } finally {file.delete()}
    }
    @Test fun serverIgnoringRangeRestartsInsteadOfAppending()=runTest {
        val file=Files.createTempFile("gundem-model",".part").toFile();file.writeText("GGUF")
        try {ModelTransfer.download(spec(),file,{}) {Reply(it,200,data)};assertArrayEquals(data,file.readBytes())} finally {file.delete()}
    }
    @Test fun badChecksumIsDeletedAndCannotBeSelected()=runTest {
        val file=Files.createTempFile("gundem-model",".part").toFile()
        try {try {ModelTransfer.download(spec().copy(sha256="0".repeat(64)),file,{}) {Reply(it,200,data)};fail("Checksum accepted")} catch(e:IllegalStateException) {assertTrue(e.message!!.contains("SHA-256"));assertFalse(file.exists())}} finally {file.delete()}
    }
    @Test fun cancellationKeepsPartialAndDoesNotMarkSuccess()=runTest {
        val file=Files.createTempFile("gundem-model",".part").toFile();file.writeText("GGUF")
        try {try {ModelTransfer.download(spec(),file,{throw CancellationException("cancel")}) {Reply(it,206,data.drop(4).toByteArray(),mapOf("Content-Range" to "bytes 4-${data.size-1}/${data.size}"))};fail("Cancelled download succeeded")} catch(e:CancellationException) {assertEquals("GGUF",file.readText())}} finally {file.delete()}
    }
    @Test fun followsHttpsRedirectAndRejectsHttpDowngrade()=runTest {
        val file=Files.createTempFile("gundem-model",".part").toFile();var calls=0
        try {ModelTransfer.download(spec(),file,{}) {if(calls++==0) Reply(it,302,byteArrayOf(),mapOf("Location" to "https://cdn.example.org/model?signature=a%2Fb")) else Reply(it,200,data).also {c->assertEquals("a%2Fb",c.url.query.substringAfter('='))}};assertEquals(2,calls)} finally {file.delete()}
        val second=Files.createTempFile("gundem-model",".part").toFile()
        try {try {ModelTransfer.download(spec(),second,{}) {Reply(it,302,byteArrayOf(),mapOf("Location" to "http://example.org/model"))};fail("HTTP allowed")} catch(e:IllegalArgumentException) {assertTrue(e.message!!.contains("HTTPS"))}} finally {second.delete()}
    }
    @Test fun badRangeAndTruncationDoNotInstall()=runTest {
        val file=Files.createTempFile("gundem-model",".part").toFile();file.writeText("GGUF")
        try {try {ModelTransfer.download(spec(),file,{}) {Reply(it,206,data,mapOf("Content-Range" to "bytes 0-20/21"))};fail("Bad range accepted")} catch(e:IllegalArgumentException) {assertEquals("GGUF",file.readText())}
            file.writeBytes(byteArrayOf());try {ModelTransfer.download(spec(),file,{}) {Reply(it,200,data.take(5).toByteArray())};fail("Truncated download accepted")} catch(e:IllegalArgumentException) {assertEquals(5L,file.length())}
        } finally {file.delete()}
    }
}
