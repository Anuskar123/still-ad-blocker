package dev.still.dns

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class FilterDownloadTest {
    @Test fun downloadsValidatedFiltersAndReopensSavedCopiesOnPhone() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "filter-network-test-").toFile()
        try {
            val repository = FilterRepository(directory)
            val selected = DownloadableFilter.entries.map { it.name }.toSet()
            repository.refresh(selected)
            for (filter in DownloadableFilter.entries) {
                val entry = repository.state.value.entries[filter]!!
                assertNull("${filter.name}: ${entry.error}", entry.error)
                assertTrue(entry.list!!.domains.size > 1000)
            }
            val cached = FilterRepository(directory, { error("Cache loading must not use the network") })
            cached.load()
            for (filter in DownloadableFilter.entries) {
                assertEquals(repository.state.value.entries[filter]!!.list, cached.state.value.entries[filter]!!.list)
            }
        } finally { directory.deleteRecursively() }
    }
}
