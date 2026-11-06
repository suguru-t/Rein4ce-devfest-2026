package com.rein4ce.roadcollector.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rein4ce.roadcollector.core.model.TowerSummary
import com.rein4ce.roadcollector.net.ApiClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * C 担当：サーバ（Cloud Run）から「みんなのタワー」を取ってくる。
 * サーバが無いときはエラーを表示するだけで、他の画面には影響しない。
 */
class GalleryViewModel : ViewModel() {
    sealed class State {
        data object Loading : State()
        data class Error(val message: String) : State()
        data class Loaded(val towers: List<TowerSummary>) : State()
    }

    var state: State by mutableStateOf(State.Loading)
        private set

    init {
        refresh()
    }

    fun refresh() {
        state = State.Loading
        viewModelScope.launch {
            state = runCatching { ApiClient.listTowers() }
                .fold(
                    onSuccess = { State.Loaded(it.sortedByDescending { t -> t.height }) },
                    onFailure = { State.Error(it.message ?: it.toString()) },
                )
        }
    }
}

@Composable
fun GalleryScreen(vm: GalleryViewModel = viewModel()) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("みんなのタワー", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { vm.refresh() }) { Text("再読み込み") }
        }
        Text(ApiClient.baseUrl, style = MaterialTheme.typography.labelSmall, color = Color(0xFF5B6675))
        Spacer(Modifier.height(12.dp))

        when (val s = vm.state) {
            GalleryViewModel.State.Loading -> CircularProgressIndicator()
            is GalleryViewModel.State.Error -> Column {
                Text("サーバに接続できません", style = MaterialTheme.typography.titleMedium)
                Text(s.message, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "ローカルで試すには ./gradlew :server:run を起動し、エミュレータから http://10.0.2.2:8080 で接続します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5B6675),
                )
            }
            is GalleryViewModel.State.Loaded -> if (s.towers.isEmpty()) {
                Text("まだタワーがありません。積んで「サーバに送信」すると、ここに並びます。")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.towers, key = { it.id }) { t ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text("高さ %.2f m".format(t.height), style = MaterialTheme.typography.titleMedium)
                                    Text("${t.partCount} 個 ・ ${t.author ?: "名無し"}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(formatDate(t.createdAt), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("M/d HH:mm", Locale.JAPAN).format(Date(epochMs))
