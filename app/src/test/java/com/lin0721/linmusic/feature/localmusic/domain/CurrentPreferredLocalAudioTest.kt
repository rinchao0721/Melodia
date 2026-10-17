package com.lin0721.linmusic.feature.localmusic.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CurrentPreferredLocalAudioTest {
    private val automatic = LocalAudioSelectionSettings(true, null, true)

    @Test fun manualChoiceMadeDuringNetworkLookupWinsImmediately() = runTest {
        var settings = automatic
        val lookup = CompletableDeferred<String?>()
        var requests = 0
        val result = async {
            resolveCurrentPreferredLocalAudio(settings, { settings }, { requests++; lookup.await() }, { true })
        }
        runCurrent()
        settings = automatic.copy(manualUri = "content://new-manual")
        lookup.complete("content://old-auto")
        assertEquals("content://new-manual", result.await())
        assertEquals(1, requests)
    }

    @Test fun disableAndExplicitUnlinkDuringLookupPreventOldAutomaticResult() = runTest {
        for (updated in listOf(automatic.copy(enabled = false), automatic.copy(automaticEnabled = false))) {
            var settings = automatic
            assertNull(resolveCurrentPreferredLocalAudio(settings, { settings },
                { settings = updated; "content://old-auto" }, { true }))
        }
    }

    @Test fun unreadableNewManualChoiceDoesNotResurrectOldAutomaticCandidate() = runTest {
        var settings = automatic
        assertNull(resolveCurrentPreferredLocalAudio(settings, { settings },
            { settings = automatic.copy(manualUri = "content://missing"); "content://old-auto" },
            { it != "content://missing" }))
    }

    @Test fun removedManualReferenceIsNeverReusedAsAnAutomaticMatch() = runTest {
        var settings = automatic.copy(manualUri = "content://old-manual")
        assertNull(resolveCurrentPreferredLocalAudio(settings, { settings }, { error("Manual path cannot trigger lookup") },
            { settings = automatic; true }))
    }

    @Test fun unchangedSettingsUseOneLookupAndOneReadabilityCheck() = runTest {
        var checks = 0
        var requests = 0
        assertEquals("content://auto", resolveCurrentPreferredLocalAudio(automatic, { automatic },
            { requests++; "content://auto" }, { checks++; true }))
        assertEquals(1, requests)
        assertEquals(1, checks)
    }
}
