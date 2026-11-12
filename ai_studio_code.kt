package com.example.coinmasterspins // Assicurati che corrisponda al tuo package

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class SpinReward(
    val title: String,
    val url: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AutoSpinRewardsScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoSpinRewardsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("cm_rewards_prefs", Context.MODE_PRIVATE) }

    var rewards by remember { mutableStateOf<List<SpinReward>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Teniamo traccia dei link già cliccati
    var claimedUrls by remember { 
        mutableStateOf(prefs.getStringSet("claimed_urls", emptySet()) ?: emptySet()) 
    }

    // Funzione che raschia il web in background
    fun scrapeLinks() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val extractedLinks = withContext(Dispatchers.IO) {
                    fetchSpinLinksFromWeb()
                }
                rewards = extractedLinks
                if (extractedLinks.isEmpty()) {
                    errorMessage = "Nessun link trovato oggi o sito temporaneamente irraggiungibile."
                }
            } catch (e: Exception) {
                errorMessage = "Errore durante il caricamento automatico: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        scrapeLinks()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coin Master Spin Auto-Bot") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { scrapeLinks() }) {
                        Text("🔄", style = MaterialTheme.typography.titleMedium)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Ricerca automatica dei nuovi link in corso...")
                    }
                }
                errorMessage != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { scrapeLinks() }) {
                            Text("Riprova")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(rewards) { item ->
                            val isClaimed = claimedUrls.contains(item.url)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isClaimed) 
                                        MaterialTheme.colorScheme.surfaceVariant 
                                    else 
                                        MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = if (isClaimed) "✅ Già riscosso" else "✨ Disponibile",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isClaimed) 
                                                MaterialTheme.colorScheme.outline 
                                            else 
                                                MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Button(
                                        enabled = !isClaimed,
                                        onClick = {
                                            // Salva il link come 'riscosso'
                                            val newSet = claimedUrls.toMutableSet().apply { add(item.url) }
                                            claimedUrls = newSet
                                            prefs.edit().putStringSet("claimed_urls", newSet).apply()

                                            // Apri Coin Master
                                            openRewardUrl(context, item.url)
                                        }
                                    ) {
                                        Text(if (isClaimed) "Preso" else "Riscatta")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Questa funzione scarica la pagina web, simula un normale browser mobile
 * ed estrae tutti i link che portano ai server di Moon Active.
 */
fun fetchSpinLinksFromWeb(): List<SpinReward> {
    val results = mutableListOf<SpinReward>()
    
    // Blog/portale sempre aggiornato ogni poche ore con i link ufficiali
    val targetUrl = "https://freespinandcoin.blogspot.com/2018/11/coin-master-free-spin-and-coin-links.html"

    val document = Jsoup.connect(targetUrl)
        .userAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
        .timeout(12000)
        .get()

    // Cerca tutti i tag <a> il cui URL contiene 'moonactive.net' o 'coinmaster'
    val elements = document.select("a[href*=\"moonactive.net\"], a[href*=\"coinmaster\"]")

    for (el in elements) {
        val linkUrl = el.attr("href").trim()
        var title = el.text().trim()

        // Se il testo del link è troppo corto (es. solo "Clicca qui"), prende il testo del paragrafo
        if (title.length < 4) {
            title = el.parent()?.text()?.take(50) ?: "Spin Gratuito"
        }

        // Escludiamo policy e link generici non-premio
        if (linkUrl.isNotEmpty() && !linkUrl.contains("terms") && !linkUrl.contains("privacy")) {
            // Evitiamo duplicati nella lista
            if (results.none { it.url == linkUrl }) {
                results.add(SpinReward(title = title, url = linkUrl))
            }
        }
    }
    return results
}

fun openRewardUrl(context: Context, urlString: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}