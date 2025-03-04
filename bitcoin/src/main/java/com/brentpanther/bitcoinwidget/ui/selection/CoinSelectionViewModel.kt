package com.brentpanther.bitcoinwidget.ui.selection

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brentpanther.bitcoinwidget.*
import com.brentpanther.bitcoinwidget.db.Widget
import com.brentpanther.bitcoinwidget.db.WidgetDatabase
import com.brentpanther.bitcoinwidget.exchange.Exchange
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class CoinSelectionViewModel : ViewModel() {

    private var allCoins = Coin.values().filterNot { it == Coin.CUSTOM }.associateBy { it.coinGeckoId }

    val coins = MutableStateFlow<SearchResponse?>(null)
    val error = MutableStateFlow<Int?>(null)

    suspend fun createWidget(context: Context, widgetId: Int, coinEntry: CoinResponse) = withContext(Dispatchers.IO) {
        val widgetType = WidgetApplication.instance.getWidgetType(widgetId)
        val dao = WidgetDatabase.getInstance(context).widgetDao()
        // pull properties from last created widget if exists
        val lastWidget = dao.getAll().lastOrNull()
        val widget = Widget(
            id = 0,
            widgetId = widgetId,
            widgetType = widgetType,
            exchange = Exchange.COINGECKO,
            coin = coinEntry.coin,
            currency = lastWidget?.currency ?: "USD",
            coinCustomId = if (coinEntry.coin == Coin.CUSTOM) coinEntry.id else null,
            coinCustomName = if (coinEntry.coin == Coin.CUSTOM) coinEntry.name else null,
            currencyCustomName = null,
            showExchangeLabel = lastWidget?.showExchangeLabel ?: false,
            showCoinLabel = lastWidget?.showCoinLabel ?: false,
            showIcon = lastWidget?.showIcon ?: true,
            numDecimals = lastWidget?.numDecimals ?: -1,
            currencySymbol = lastWidget?.currencySymbol,
            theme = lastWidget?.theme ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Theme.MATERIAL else Theme.SOLID,
            nightMode = lastWidget?.nightMode ?: NightMode.SYSTEM,
            coinUnit = if (widgetType == WidgetType.VALUE) null else coinEntry.coin.getUnits().firstOrNull()?.text,
            currencyUnit = null,
            customIcon = coinEntry.large,
            lastUpdated = 0,
            showAmountLabel = lastWidget?.showAmountLabel ?: (widgetType == WidgetType.VALUE),
            amountHeld = if (widgetType == WidgetType.VALUE) 1.0 else null,
            useInverse = false,
            state = WidgetState.DRAFT
        )
        dao.insert(widget)
    }

    var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            error.emit(null)
            coins.emit(SearchResponse(emptyList(), true))
            val responses = searchCoinGecko(query)
            var coinResults = when {
                responses != null -> {
                    responses.coins.map {
                        allCoins[it.id]?.let { coin ->
                            CoinResponse(coin.name, coin.coinName, coin.getSymbol(), null, null, coin)
                        } ?: it.apply { coin = Coin.CUSTOM }
                    }
                }
                else -> {
                    allCoins.values.filter {
                        it.coinName.contains(query, ignoreCase = true) || it.getSymbol().contains(query, ignoreCase = true)
                    }.map { coin ->
                        CoinResponse(coin.name, coin.coinName, coin.getSymbol(), null, null, coin)
                    }
                }
            }
            coinResults = coinResults.sortedWith(
                compareBy(
                    { it.coin == Coin.CUSTOM },
                    { it.name.uppercase() }
                ))
            coins.emit(SearchResponse(coinResults, false))
        }
    }

    private fun searchCoinGecko(query: String): SearchResponse? {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.coingecko.com/api/v3/search?query=$query")
                .addHeader("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    error.tryEmit(null)
                    return Gson().fromJson(response.body!!.charStream(), SearchResponse::class.java)
                } else {
                    error.tryEmit(R.string.search_error)
                    return null
                }
            }
        } catch (e: IOException) {
            error.tryEmit(R.string.search_error)
            return null
        }
    }

    fun removeWidget(context: Context, widgetId: Int) = viewModelScope.launch(Dispatchers.IO) {
        WidgetDatabase.getInstance(context).widgetDao().delete(intArrayOf(widgetId))
    }
}