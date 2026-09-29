package com.makeeb.engine.packs

import com.makeeb.core.common.Sha256
import com.makeeb.engine.dictionary.InstalledPack
import com.makeeb.engine.dictionary.pack.MkdWord
import com.makeeb.engine.dictionary.pack.MkdWriter
import com.makeeb.testing.FakeHttpTransport
import com.makeeb.testing.FakePackFiles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PackInstallerTest {
    private val release = "https://github.com/owner/repo/releases/download/dictionaries-v1/"
    private val catalogueUrl = release + "catalogue.json"
    private val transport = FakeHttpTransport().apply { chunkSize = 64 }
    private val files = FakePackFiles()

    private fun pack(language: String, vararg words: String = arrayOf("alma", "körte", "szilva", "barack")): ByteArray =
        MkdWriter.write(words.mapIndexed { i, w -> MkdWord(w, 200 - i) }, mapOf("language" to language))

    private val hungarian = pack("hu")
    private val german = pack("de", "der", "und", "Straße")

    private fun entry(language: String, bytes: ByteArray, file: String = "${language.replace('-', '_')}.mkd") = PackEntry(
        language = language,
        name = language,
        file = file,
        url = file,
        size = bytes.size.toLong(),
        sha256 = Sha256.hex(bytes),
        mkdVersion = "1.1",
        words = 4,
        nextWords = false,
        licence = "CC-BY-4.0",
        attribution = "test",
    )

    /** Publishes a catalogue of [packs] and serves each pack's bytes at its URL. */
    private fun publish(vararg packs: Pair<PackEntry, ByteArray>) {
        transport.serve(catalogueUrl, PackCatalogue(packs.map { it.first }).toJson().encodeToByteArray())
        for ((entry, bytes) in packs) transport.serve(release + entry.file, bytes)
    }

    private fun TestScope.installer(url: String = catalogueUrl) =
        PackInstaller(url, transport, files, backgroundScope, io = StandardTestDispatcher(testScheduler))

    @Test
    fun withoutACatalogueUrlNothingIsOffered() = runTest {
        val installer = installer(url = "")
        installer.refresh()
        runCurrent()
        assertEquals(CatalogueState.NotConfigured, installer.state.value.catalogue)
        assertEquals(PackStatus.Unknown(CatalogueState.NotConfigured), installer.state.value.statusOf("hu"))
        assertEquals(PackStatus.BuiltIn, installer.state.value.statusOf("en-GB"))
        assertEquals(setOf("en"), installer.state.value.lexiconLanguages)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun offersWhatTheCatalogueLists() = runTest {
        publish(entry("hu", hungarian) to hungarian, entry("de", german) to german)
        val installer = installer()
        installer.refresh()
        runCurrent()
        assertEquals(PackStatus.Available(entry("hu", hungarian), failure = null), installer.state.value.statusOf("hu"))
        assertEquals(PackStatus.NotOffered, installer.state.value.statusOf("sv"))
    }

    @Test
    fun installsAPackOnceItIsChecked() = runTest {
        publish(entry("hu", hungarian) to hungarian)
        val installer = installer()
        installer.refresh()
        runCurrent()

        transport.pauseAfter = 128
        installer.install("hu")
        runCurrent()
        val downloading = assertIs<PackStatus.Downloading>(installer.state.value.statusOf("hu"))
        assertTrue(downloading.received in 1 until hungarian.size, "progress while the body arrives")
        assertTrue(files.files.isEmpty(), "nothing is visible before it is checked")

        transport.resume()
        runCurrent()
        val name = InstalledPack.fileName("hu", Sha256.hex(hungarian))
        assertEquals(setOf(name), files.files.keys)
        assertTrue(files.files.getValue(name).contentEquals(hungarian))
        assertTrue(files.pending.isEmpty())
        val installed = assertIs<PackStatus.Installed>(installer.state.value.statusOf("hu"))
        assertNull(installed.update)
        assertEquals(setOf("en", "hu"), installer.state.value.lexiconLanguages)
    }

    @Test
    fun aDownloadThatIsntTheCataloguesPackIsThrownAway() = runTest {
        val flipped = hungarian.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        // Hash and size match, but it isn't a pack; a German pack posing as Hungarian; a flipped byte.
        val garbage = ByteArray(hungarian.size) { 7 }
        val cases = listOf(
            entry("hu", hungarian) to flipped,
            entry("hu", garbage) to garbage,
            entry("hu", german) to german,
        )
        for ((entry, served) in cases) {
            publish(entry to served)
            val installer = installer()
            installer.refresh()
            runCurrent()
            installer.install("hu")
            runCurrent()
            assertEquals(PackStatus.Available(entry, InstallFailure.Corrupt), installer.state.value.statusOf("hu"))
            assertTrue(files.files.isEmpty() && files.pending.isEmpty(), "nothing kept")
        }
    }

    @Test
    fun aWrongLengthStopsTheDownloadAtOnce() = runTest {
        val entry = entry("hu", hungarian)
        transport.serve(catalogueUrl, PackCatalogue(listOf(entry)).toJson().encodeToByteArray())
        transport.responses[release + entry.file] = FakeHttpTransport.Response.Body(hungarian + hungarian, declaredLength = -1)
        val installer = installer()
        installer.refresh()
        runCurrent()
        installer.install("hu")
        runCurrent()
        assertEquals(PackStatus.Available(entry, InstallFailure.Corrupt), installer.state.value.statusOf("hu"))
        assertTrue(files.files.isEmpty() && files.pending.isEmpty())
    }

    @Test
    fun offlineTheInstalledPacksStillCount() = runTest {
        files.files[InstalledPack.fileName("hu", Sha256.hex(hungarian))] = hungarian
        transport.offline = true
        val installer = installer()
        installer.refresh()
        runCurrent()
        assertEquals(CatalogueState.Unreachable, installer.state.value.catalogue)
        assertIs<PackStatus.Installed>(installer.state.value.statusOf("hu"))
        assertEquals(PackStatus.Unknown(CatalogueState.Unreachable), installer.state.value.statusOf("de"))
        assertEquals(setOf("en", "hu"), installer.state.value.lexiconLanguages)

        // Back online, the next refresh tries the catalogue again.
        transport.offline = false
        publish(entry("de", german) to german)
        installer.refresh()
        runCurrent()
        assertIs<PackStatus.Available>(installer.state.value.statusOf("de"))
    }

    @Test
    fun aFailedDownloadCanBeTriedAgain() = runTest {
        val entry = entry("hu", hungarian)
        transport.serve(catalogueUrl, PackCatalogue(listOf(entry)).toJson().encodeToByteArray())
        val installer = installer()
        installer.refresh()
        runCurrent()
        installer.install("hu")
        runCurrent()
        assertEquals(PackStatus.Available(entry, InstallFailure.Network), installer.state.value.statusOf("hu"), "404")

        transport.serve(release + entry.file, hungarian)
        installer.install("hu")
        runCurrent()
        assertIs<PackStatus.Installed>(installer.state.value.statusOf("hu"))
    }

    @Test
    fun aFullDiskIsAStorageFailure() = runTest {
        publish(entry("hu", hungarian) to hungarian)
        files.failWrites = true
        val installer = installer()
        installer.refresh()
        runCurrent()
        installer.install("hu")
        runCurrent()
        assertEquals(PackStatus.Available(entry("hu", hungarian), InstallFailure.Storage), installer.state.value.statusOf("hu"))
        assertTrue(files.pending.isEmpty())
    }

    @Test
    fun cancellingKeepsNothing() = runTest {
        publish(entry("hu", hungarian) to hungarian)
        val installer = installer()
        installer.refresh()
        runCurrent()
        transport.pauseAfter = 128
        installer.install("hu")
        runCurrent()
        assertIs<PackStatus.Downloading>(installer.state.value.statusOf("hu"))
        installer.cancel("hu")
        runCurrent()
        assertEquals(PackStatus.Available(entry("hu", hungarian), failure = null), installer.state.value.statusOf("hu"))
        assertTrue(files.files.isEmpty() && files.pending.isEmpty())
    }

    @Test
    fun anUpdateReplacesTheOldVersion() = runTest {
        val old = pack("hu", "alma", "körte")
        val oldName = InstalledPack.fileName("hu", Sha256.hex(old))
        files.files[oldName] = old
        publish(entry("hu", hungarian) to hungarian)
        val installer = installer()
        installer.refresh()
        runCurrent()
        val installed = assertIs<PackStatus.Installed>(installer.state.value.statusOf("hu"))
        assertEquals(entry("hu", hungarian), installed.update)

        installer.install("hu")
        runCurrent()
        assertEquals(setOf(InstalledPack.fileName("hu", Sha256.hex(hungarian))), files.files.keys, "the old version is gone")
        assertNull(assertIs<PackStatus.Installed>(installer.state.value.statusOf("hu")).update)
    }

    @Test
    fun removingDeletesEveryVersion() = runTest {
        files.files[InstalledPack.fileName("hu", Sha256.hex(hungarian))] = hungarian
        files.files[InstalledPack.fileName("hu", "f".repeat(64))] = hungarian
        publish(entry("hu", hungarian) to hungarian)
        val installer = installer()
        installer.refresh()
        runCurrent()
        installer.remove("hu")
        runCurrent()
        assertTrue(files.files.isEmpty())
        assertIs<PackStatus.Available>(installer.state.value.statusOf("hu"))
        assertEquals(setOf("en"), installer.state.value.lexiconLanguages)
    }

    @Test
    fun aKeyboardLanguageTakesItsRegionalPack() = runTest {
        val portuguese = pack("pt-BR", "de", "que", "não")
        publish(entry("pt-BR", portuguese) to portuguese)
        val installer = installer()
        installer.refresh()
        runCurrent()
        assertIs<PackStatus.Available>(installer.state.value.statusOf("pt"))
        installer.install("pt")
        runCurrent()
        assertEquals(setOf(InstalledPack.fileName("pt-BR", Sha256.hex(portuguese))), files.files.keys)
        assertIs<PackStatus.Installed>(installer.state.value.statusOf("pt"))
        assertTrue("pt" in installer.state.value.lexiconLanguages)
    }

    @Test
    fun refreshingSweepsAwayWhatAKilledDownloadLeft() = runTest {
        files.pending["hu-0123456789abcdef.mkd"] = ByteArray(10)
        transport.offline = true
        val installer = installer()
        installer.refresh()
        runCurrent()
        assertTrue(files.pending.isEmpty())
    }

    @Test
    fun setupIsDoneWhenEverySelectedLanguageHasItsDictionary() = runTest {
        publish(entry("hu", hungarian) to hungarian, entry("de", german) to german)
        val installer = installer()
        installer.refresh()
        runCurrent()
        assertTrue(installer.state.value.hasDictionaries(listOf("en", "en-GB")), "English is built in")
        assertTrue(!installer.state.value.hasDictionaries(listOf("hu", "en")))
        installer.install("hu")
        runCurrent()
        assertTrue(installer.state.value.hasDictionaries(listOf("hu", "en")))
        assertTrue(!installer.state.value.hasDictionaries(listOf("hu", "de")))
    }

    @Test
    fun anUnreadableCatalogueOffersNothing() = runTest {
        transport.serve(catalogueUrl, "<html>Not Found</html>".encodeToByteArray())
        val installer = installer()
        installer.refresh()
        runCurrent()
        assertEquals(CatalogueState.Unreadable, installer.state.value.catalogue)
        installer.install("hu")
        runCurrent()
        assertEquals(listOf(catalogueUrl), transport.requests)
    }
}
