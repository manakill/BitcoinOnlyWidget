package com.brentpanther.bitcoinwidget.strategy.data

import android.util.Log
import com.brentpanther.bitcoinwidget.WidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

open class PriceWidgetDataStrategy(widgetId: Int) : WidgetDataStrategy(widgetId) {

    override suspend fun loadData(manual: Boolean): Unit = withContext(Dispatchers.IO) {
        val widget = widget ?: return@withContext
        val config = getConfig()
        if (manual) {
            delay(750)
        }
        try {
            val currency = widget.currencyCustomName ?: widget.currency
            val coin = widget.coinCustomId ?: widget.coinCustomName ?: widget.coin.getSymbol()
            val value = widget.exchange.getValue(coin, currency)
            if (widget.state != WidgetState.DRAFT) widget.state = WidgetState.CURRENT
            if (value == null) {
                throw IllegalArgumentException()
            } else {
                setData(value)
            }
            Log.i(TAG, "$widgetId: downloaded value $value")
        } catch (e: IOException) {
            // unable to reach exchange. potentially mark data as stale
            if (widget.isOld(config.refresh)) {
                if (widget.state != WidgetState.DRAFT)  widget.state = WidgetState.STALE
            }
            Log.w(TAG, "Error getting value from exchange: ${widget.exchange}.", e)
        } catch (e: Exception) {
            // error with data from exchange. potentially mark as error
            if (widget.isOld(config.refresh) || widget.lastValue == null) {
                if (widget.state != WidgetState.DRAFT) widget.state = WidgetState.ERROR
            }
            Log.e(TAG, "Error parsing value from exchange: ${widget.exchange}.", e)
        }
    }

    open fun setData(value: String) {
        widget?.apply {
            lastValue = value
            lastUpdated = System.currentTimeMillis()
        }
    }

    companion object {
        private val TAG = PriceWidgetDataStrategy::class.java.simpleName
    }
}
