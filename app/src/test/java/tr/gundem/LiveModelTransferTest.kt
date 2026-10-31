package tr.gundem
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.nio.file.Files

/** Opt-in real internet test using the production downloader, no mock HTTP response. */
class LiveModelTransferTest {
    @Test fun realPinnedModelDownloadsWithProductionCode()=runBlocking<Unit> {
        Assume.assumeTrue(System.getenv("GUNDEM_LIVE_MODEL_DOWNLOAD")=="1")
        val file=Files.createTempFile("gundem-real-download",".part").toFile()
        try {
            var maximum=0L
            ModelTransfer.download(RECOMMENDED_MODEL,file,{maximum=maxOf(maximum,it)})
            assertEquals(RECOMMENDED_MODEL.size,file.length());assertEquals(RECOMMENDED_MODEL.size,maximum)
        } finally {file.delete()}
    }
}
