package tr.gundem
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Small real Android ABI smoke; full real-model pipeline is additionally tested on the host. */
@RunWith(AndroidJUnit4::class)
class NativeBridgeTest {
    @Test fun actualAndroidGgufLoadAndConstrainedToken()=runBlocking<Unit> {
        val path=InstrumentationRegistry.getArguments().getString("modelPath")
        Assume.assumeTrue("GGUF test dosyası verilmedi",!path.isNullOrBlank())
        val app=ApplicationProvider.getApplicationContext<GundemApp>()
        try {
            app.model.session(Settings(modelPath=path!!,modelHash="android-smoke")) {
                val output=NativeLlama.generate("JSON üret.","Bir haberin etki alanı.","root ::= \"{\\\"etki\\\":\" [0-3] \"}\"",1)
                assertTrue(output,output.startsWith("CUT\n"))
                assertFalse(OutputValidator.analysis(output.substringAfter('\n'),false).complete)
            }
        } finally { app.model.release() }
    }
}
