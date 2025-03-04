package com.brentpanther.bitcoinwidget

import com.brentpanther.bitcoinwidget.exchange.Exchange.valueOf
import com.brentpanther.bitcoinwidget.exchange.ExchangeData
import com.brentpanther.bitcoinwidget.exchange.ExchangeHelper
import org.junit.Test
import java.io.InputStream
import java.util.*


class ExchangeTest {

    private fun loadJSON(): InputStream {
        return ClassLoader.getSystemResourceAsStream("raw/cryptowidgetcoins_v2.json")
    }

    @Test
    fun removedCoins() {
        ExchangeHelper.useCache = false
        val coins = EnumSet.allOf(Coin::class.java).sorted()
        for (coin in coins) {
            println("Checking $coin")
            val data = ExchangeData(coin, loadJSON())
            for (currency in data.currencies.sorted()) {
                for (exchange in data.getExchanges(currency).toList().parallelStream()) {
                    try {
                        var coinName = data.getExchangeCoinName(exchange)
                        var currencyName = data.getExchangeCurrencyName(exchange, currency)
                        if (coinName == null) coinName = coin.getSymbol()
                        if (currencyName == null) currencyName = currency
                        if (coinName == currencyName) continue
                        valueOf(exchange).getValue(coinName, currencyName)!!.toDouble()
                    } catch (e: Exception) {
                        System.err.println("Failure: $coin $exchange $currency: ${e.message}")
                    }
                    Thread.sleep(1500)
                }
            }
        }
    }

}