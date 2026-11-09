package com.rein4ce.roadcollector.data

import android.util.AtomicFile
import com.rein4ce.roadcollector.core.challenge.ChallengeRules
import com.rein4ce.roadcollector.core.challenge.ChallengeScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * 3分チャレンジの記録のファイル（filesDir/challenge_records.json）。AtomicFile で書くので、書いている途中で落ちても壊れない。
 * 確定した結果の保存は、画面や ViewModel が無くなっても最後まで終える（NonCancellable）
 */
class ChallengeStorage(private val directory: File) {
    private val file = AtomicFile(File(directory, "challenge_records.json"))

    suspend fun update(score: ChallengeScore?): List<ChallengeScore> =
        withContext(if (score == null) Dispatchers.IO else Dispatchers.IO + NonCancellable) {
            synchronized(fileLock) {
                val records = ChallengeRecords(
                    read = {
                        try {
                            file.openRead().bufferedReader().use { it.readText() }
                        } catch (e: FileNotFoundException) {
                            // 無ければ空。あるのに開けないファイルは上書きしない
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
                    // 読めない中身は消さずに別の名前で残し（あとで中を見られるように）、空から始める
                    quarantine = {
                        val broken = File(directory, "challenge_records.broken-${System.currentTimeMillis()}.json")
                        if (!file.baseFile.renameTo(broken)) file.delete()
                    },
                )
                if (score == null) records.ranking(ChallengeRules.ID) else records.add(score)
            }
        }

    private companion object {
        val fileLock = Any()
    }
}
