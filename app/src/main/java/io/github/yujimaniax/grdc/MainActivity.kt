package io.github.yujimaniax.grdc

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yujimaniax.grdc.ui.theme.GithubReleaseDownloadCounterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private val _uri = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = checkIntent(intent)
        _uri.value = uri

        enableEdgeToEdge()
        setContent {
            GithubReleaseDownloadCounterTheme {
                MainScreen(_uri.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = checkIntent(intent)
        _uri.value = uri
    }
}

private fun checkIntent(intent: Intent): String {
    if (intent.action == Intent.ACTION_SEND) {
        intent.clipData.let { d ->
            if (d != null) {
                if (d.itemCount > 0) {
                    val uri = d.getItemAt(0).text.toString()
                    return uri
                }
            }
        }
    }

    return ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(uri: String) {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(uri) }
    var error by remember { mutableStateOf("") }
    val result = remember { mutableStateListOf<DownloadAssets>() }
    var btn by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    SideEffect {
        target = uri
        error = ""
        result.clear()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding),
        ){
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 8.dp)
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    text = "Githubのダウンロード数を表示する",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = target,
                        onValueChange = {
                            target = it
                            error = ""
                            result.clear()
                        },
                        label = { Text(text = "表示するReleasesのurl") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.weight(1f),
                    )
                    // urlの消去アイコン
                    Icon(
                        Icons.Default.Clear,
                        tint = Color.Red,
                        contentDescription = "clear",
                        modifier = Modifier.clickable {
                            target = ""
                            result.clear()
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, top = 16.dp, end = 16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        Button(
                            enabled = btn,
                            modifier = Modifier.focusable(),
                            onClick = {
                                focusManager.clearFocus()
                                if (target.isEmpty()) {
                                    error = "urlを指定して下さい"
                                } else {
                                    error = ""
                                    btn = false
                                    result.clear()
                                    scope.launch(Dispatchers.IO) {
                                        target = replaceScheme(target)
                                        if(checkTargetUrl(target)){
                                            val type = checkUrlType(target)
                                            when (type) {
                                                UrlType.API_RELEASES -> {
                                                    val apiUrl = URL(target)
                                                    val str = getResponse(apiUrl
                                                    ) { code ->
                                                        if (code < 0) {
                                                            error = "不明なエラー"
                                                        } else {
                                                            error = "正しく接続出来ませんでした"
                                                        }
                                                    }
                                                    if (str.isNotEmpty()) {
                                                        val list = parseRelease(str)
                                                        result.addAll(list)
                                                    }
                                                }
                                                UrlType.API_TAGS -> {
                                                    val apiUrl = URL(target)
                                                    val str = getResponse(apiUrl
                                                    ) { code ->
                                                        if (code < 0) {
                                                            error = "不明なエラー"
                                                        } else {
                                                            error = "正しく接続出来ませんでした"
                                                        }
                                                    }
                                                    if (str.isNotEmpty()) {
                                                        val data = parseTags(str)
                                                        result.add(data)
                                                    }
                                                }
                                                UrlType.RELEASES -> {
                                                    val apiUrl = replaceUrl(target)
                                                    val str = getResponse(apiUrl
                                                    ) { code ->
                                                        if (code < 0) {
                                                            error = "不明なエラー"
                                                        } else {
                                                            error = "正しく接続出来ませんでした"
                                                        }
                                                    }
                                                    if (str.isNotEmpty()) {
                                                        val list = parseRelease(str)
                                                        result.addAll(list)
                                                    }
                                                }
                                                UrlType.TAGS -> {
                                                    val apiUrl = replaceUrl(target)
                                                    val str = getResponse(apiUrl
                                                    ) { code ->
                                                        if (code < 0) {
                                                            error = "不明なエラー"
                                                        } else {
                                                            error = "正しく接続出来ませんでした"
                                                        }
                                                    }
                                                    if (str.isNotEmpty()) {
                                                        val data = parseTags(str)
                                                        result.add(data)
                                                    }
                                                }
                                                else -> {
                                                    error = "URLが期待する形式ではありません"
                                                }
                                            }
                                        }else{
                                            error = "urlに接続出来ませんでした"
                                        }
                                        btn = true
                                    }
                                }
                            }
                        ) {
                            Text(text = "取得")
                        }
                    }
                    Text(text = error, color = Color.Red)

                    var tags = ""
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(result) { r ->
                            if (!tags.equals(r.tag)) {
                                Text(text = "")
                                Text(text = r.tag, fontWeight = FontWeight.Bold)
                                HorizontalDivider()
                                tags = r.tag
                            }
                            Text(text = r.apk)
                            Text(text = " ${String.format("%,d", r.count)} 回")
                        }
                    }
                }
            }
        }
    }
}

private suspend fun checkTargetUrl(target: String): Boolean {
    try {
        val url = URL(target)
        val con = url.openConnection() as HttpURLConnection

        con.connectTimeout = 20000
        con.readTimeout = 20000
        con.requestMethod = "GET"

        con.connect()

        val responseCode = con.responseCode

        return responseCode == HttpURLConnection.HTTP_OK
    } catch (e: Exception) {
        Log.e("checkTargetUrl", e.message.toString())
        return false
    }
}

enum class UrlType {
    OTHER,
    API_RELEASES,
    API_TAGS,
    RELEASES,
    TAGS,
}

private fun checkUrlType(target: String): UrlType {
    try {
        val url = URL(target)

        Log.d("checkUrlType", "host = ${url.host} path = ${url.path}")

        when (url.host) {
            "api.github.com" -> {
                val paths = url.path.split("/")
                if (paths.count() > 4) {
                    if (paths[1].equals("repos")) {
                        if (paths[4].equals("releases")) {
                            if (paths.count() == 5) {
                                Log.v("checkUrlType", "API_RELEASES")
                                return UrlType.API_RELEASES
                            } else {
                                if (paths.count() == 7) {
                                    if (paths[5].equals("tags")) {
                                        Log.v("checkUrlType", "API_TAGS")
                                        return UrlType.API_TAGS
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "github.com" -> {
                val paths = url.path.split("/")
                if (paths.count() > 3) {
                    if (paths[3].equals("releases")) {
                        if (paths.count() == 4) {
                            Log.v("checkUrlType", "RELEASES")
                            return UrlType.RELEASES
                        } else {
                            if (paths.count() == 6) {
                                if (paths[4].equals("tag")) {
                                    Log.v("checkUrlType", "TAGS")
                                    return UrlType.TAGS
                                }
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    } catch (e: Exception) {
        Log.e("checkUrlType", e.message.toString())
    }

    Log.v("checkUrlType", "OTHER")
    return UrlType.OTHER
}

private suspend fun getResponse(url: URL, onError: (Int) -> Unit): String {
    try {
        val con = url.openConnection() as HttpURLConnection

        con.connectTimeout = 20000
        con.readTimeout = 20000
        con.requestMethod = "GET"
        con.setRequestProperty(
            "Accept",
            "application/vnd.github.v3+json"
        )

        con.connect()

        val responseCode = con.responseCode

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val str = con.inputStream.bufferedReader(Charsets.UTF_8)
                .use { br ->
                    br.readLines().joinToString(separator = "")
                }
            return str
        } else {
            onError(responseCode)
        }
    } catch (e: Exception) {
        Log.e("getResponse", e.message.toString())
        onError(-1)
    }

    return ""
}

private fun parseRelease(str: String): List<DownloadAssets> {
    try {
        val format = Json { ignoreUnknownKeys = true }
        val list = mutableListOf<DownloadAssets>()
        val json = format.decodeFromString<List<GithubDownload>>(str)
        json.forEach { a ->
            a.assets.forEach { aa ->
                list.add(DownloadAssets(a.tagName, aa.name, aa.downloadCount))
            }
        }
        return list
    } catch (e: Exception) {
        Log.e("parseRelease", e.message.toString())
    }

    Log.v("parseRelease", str)
    return listOf()
}

private fun parseTags(str: String): DownloadAssets {
    try {
        val format = Json { ignoreUnknownKeys = true }
        val json = format.decodeFromString<GithubDownload>(str)
        json.assets.forEach { aa ->
            return DownloadAssets("", aa.name, aa.downloadCount)
        }
    } catch (e: Exception) {
        Log.e("parseTags", e.message.toString())
    }

    Log.v("parseTags", str)
    return DownloadAssets("", "",0)
}

data class DownloadAssets(
    val tag: String,
    val apk: String,
    val count: Int,
)

private fun replaceUrl(target: String): URL {
    val url = URL(target)

    val dest = url.protocol + "://api.github.com/repos" + url.path.replace("/tag/", "/tags/")
    return URL(dest)
}

private fun replaceScheme(target: String): String {
    val url = URL(target)
    return if (url.protocol.equals("http")) {
        "https://" + url.host + url.path
    } else {
        target
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GithubReleaseDownloadCounterTheme {
        MainScreen("aaa")
    }
}