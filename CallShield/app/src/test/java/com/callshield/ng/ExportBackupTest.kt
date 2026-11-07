package com.callshield.ng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ExportBackupTest {

    private val lagos: TimeZone = TimeZone.getTimeZone("Africa/Lagos")
    private val hour = 3_600_000L
    private val t0 = 1_762_473_600_000L   // 2025-11-07 00:00 UTC = 01:00 Lagos

    @Test fun csvHasHeaderAndRowsOldestFirst() {
        val csv = CsvExport.build(
            listOf(
                CsvExport.Row(t0 + 2 * hour, "08031234567", "Not in your contacts", false),
                CsvExport.Row(t0, "02012345678", "Called on 3 days this week, calling at night", true)
            ),
            lagos
        )
        val lines = csv.trimEnd().split("\r\n")
        assertEquals("Date,Time,Number,Loan spam,Why it was blocked", lines[0])
        assertEquals("2025-11-07,01:00,0201 234 5678,Yes,\"Called on 3 days this week, calling at night\"", lines[1])
        assertEquals("2025-11-07,03:00,0803 123 4567,No,Not in your contacts", lines[2])
    }

    @Test fun csvEscapesQuotes() {
        assertEquals("\"He said \"\"pay\"\"\"", CsvExport.escape("He said \"pay\""))
        assertEquals("plain", CsvExport.escape("plain"))
    }

    @Test fun csvFileNameHasDate() {
        assertEquals("callshield-turned-away-2025-11-07.csv", CsvExport.fileName(t0, lagos))
    }

    private val sample = BackupData(
        enabled = true, blockUnknown = true, blockHidden = false, allowCalledBefore = false,
        allowRepeatCallers = true, silenceInsteadOfReject = true, notifyOnBlock = false,
        askAboutUnknown = true, detectionLevel = 2,
        allowList = setOf("08031112222"),
        blockList = setOf("0700*", "09012345678"),
        detectedSpam = setOf("02012345678")
    )

    @Test fun backupRoundTrips() {
        val restored = Backup.fromJson(Backup.toJson(sample, t0))
        assertEquals(sample, restored)
    }

    @Test fun backupRejectsOtherFiles() {
        assertNull(Backup.fromJson("{\"hello\": 1}"))
        assertNull(Backup.fromJson("not json at all"))
    }

    @Test fun backupDropsBadEntries() {
        val text = """{"format":"CallShield backup","version":1,
            "block":["0*", "", "+234 803 123 4567", "0700*"]}"""
        val d = Backup.fromJson(text)!!
        assertEquals(setOf("08031234567", "0700*"), d.blockList)
        assertTrue(d.allowList.isEmpty())
        assertFalse(d.blockList.contains("0*"))
        assertEquals(1, d.detectionLevel)   // missing settings fall back to defaults
    }
}
