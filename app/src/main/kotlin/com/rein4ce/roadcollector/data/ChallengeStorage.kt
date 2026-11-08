package com.rein4ce.roadcollector.data

import android.util.AtomicFile
import com.rein4ce.roadcollector.core.challenge.ChallengeRules
import com.rein4ce.roadcollector.core.challenge.ChallengeScore
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** A committed result must finish saving even if the screen or its ViewModel is removed. */
class ChallengeStorage(directory: File) {
    private val file = AtomicFile(File(directory, "challenge_records.json"))

    suspend fun update(score: ChallengeScore?): List<ChallengeScore> =
        withContext(if (score == null) Dispatchers.IO else Dispatchers.IO + NonCancellable) {
            synchronized(fileLock) {
                val records = ChallengeRecords(
                    read = {
                        try {
                            file.openRead().bufferedReader().use { it.readText() }
                        } catch (e: FileNotFoundException) {
                            // Missing is empty; an existing unreadable file must not be overwritten.
                            if (file.baseFile.exists()) throw e
                            null
                        }
                    },
                    write = { text ->
                        val stream = file.startWrite()
                        try {
                            stream.write(text.toByteArray(Charsets.UTF_8))
                            file.finishWrite(stream)
                        } catch (e: Exception) {
                            file.failWrite(stream)
                            throw e
                        }
                    },
                )
                if (score == null) records.ranking(ChallengeRules.ID) else records.add(score)
            }
        }

    private companion object { val fileLock = Any() }
}
