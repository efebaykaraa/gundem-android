package tr.gundem
import org.junit.Test
import org.junit.Assert.*
class XAccountsTest {
    @Test fun handlesAndProfileLinksHaveOneIdentity() {
        assertEquals("okuyankemal",xHandle(" @OkuyanKemal "));assertEquals(xSource("@OkuyanKemal").id,xSource("https://x.com/OkuyanKemal").id)
        assertEquals("tkpninsesi",xHandle("https://twitter.com/tkpninsesi/"))
    }
    @Test fun rejectsNonProfilesAndReservedNames() {
        listOf("https://x.com/user/status/123","https://evil.org/user","https://x.com.evil.org/user","hello world","a".repeat(16),"../x","search").forEach {input->assertThrows(input,IllegalArgumentException::class.java) {xHandle(input)}}
    }
    @Test fun defaultsAndPerAccountFeedResolution() {
        assertTrue(DEFAULT_X_ACCOUNTS.any {it.id=="x:tkpninsesi"});assertTrue(DEFAULT_X_ACCOUNTS.any {it.id=="x:okuyankemal"})
        assertEquals("https://nitter.example.org/prefix/tkpninsesi/rss",resolveXSource(xSource("tkpninsesi"),"https://nitter.example.org/prefix/").endpoint)
        assertEquals("https://rss.example.org/custom",resolveXSource(xSource("tkpninsesi").copy(endpoint="https://rss.example.org/custom"),"").endpoint)
        assertEquals("$DEFAULT_X_SERVER/tkpninsesi/rss",resolveXSource(xSource("tkpninsesi"),"").endpoint)
        assertThrows(IllegalArgumentException::class.java) {nitterFeed("http://insecure.org","tkpninsesi")}
    }

}
