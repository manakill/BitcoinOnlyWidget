package com.brentpanther.bitcoinwidget.strategy.display

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.IdRes
import com.brentpanther.bitcoinwidget.Coin
import com.brentpanther.bitcoinwidget.R
import com.brentpanther.bitcoinwidget.WidgetState.ERROR
import com.brentpanther.bitcoinwidget.WidgetState.STALE
import com.brentpanther.bitcoinwidget.db.Widget
import com.brentpanther.bitcoinwidget.strategy.TextViewAutoSizeHelper
import com.brentpanther.bitcoinwidget.strategy.presenter.WidgetPresenter
import java.io.File
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

abstract class PriceWidgetDisplayStrategy(context: Context, widget: Widget, widgetPresenter: WidgetPresenter) :
    WidgetDisplayStrategy(context, widget, widgetPresenter) {

    protected fun getPriceFormat(adjustedAmount: Double): NumberFormat {
        val symbol = widget.currencySymbol
        val nf = if (Coin.COIN_NAMES.contains(widget.currency)) {
            // virtual currency
            val format = Coin.getVirtualCurrencyFormat(widget.currency, symbol == "none")
            DecimalFormat(format)
        } else {
            when (symbol) {
                null -> {
                    DecimalFormat.getCurrencyInstance().apply {
                        currency = Currency.getInstance(widget.currency)
                    }
                }
                "none" -> NumberFormat.getNumberInstance()
                else -> {
                    (DecimalFormat.getCurrencyInstance() as DecimalFormat).also {
                        val decimalFormatSymbols = it.decimalFormatSymbols
                        decimalFormatSymbols.currencySymbol = symbol
                        it.decimalFormatSymbols = decimalFormatSymbols
                    }
                }
            }
        }
        if (widget.numDecimals >= 0) {
            nf.maximumFractionDigits = widget.numDecimals
            nf.minimumFractionDigits = widget.numDecimals
        } else if (adjustedAmount > 1000) {
            nf.maximumFractionDigits = 0
        }
        return nf
    }

    protected fun updateState() {
        with(widgetPresenter) {
            when(widget.state) {
                STALE -> {
                    show(R.id.state)
                    setImageViewResource(R.id.state, R.drawable.ic_outline_stale)
                    setOnClickMessage(appContext, R.string.state_stale)
                }
                ERROR -> {
                    show(R.id.state)
                    setImageViewResource(R.id.state, R.drawable.ic_outline_warning_amber_24)
                    setOnClickMessage(appContext, R.string.state_error)
                }
                else -> gone(R.id.state)
            }
        }
    }

    protected fun getView(@IdRes layoutId: Int): TextView {
        val layout = widget.theme.getLayout(widget.nightMode.isDark(appContext), widget.widgetType)
        val vg = LayoutInflater.from(appContext).inflate(layout, null) as ViewGroup
        return vg.findViewById(layoutId)
    }

    protected fun updateIcon() {
        if (!widget.showIcon) {
            widgetPresenter.gone(R.id.icon)
            return
        }
        widgetPresenter.show(R.id.icon)
        val customIcon = widget.coinCustomId
        if (customIcon != null) {
            val file = File(appContext.filesDir, "icons/$customIcon")
            if (file.exists()) {
                file.inputStream().use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    widgetPresenter.setImageViewBitmap(R.id.icon, bitmap)
                }
            }
        } else {
            val isDark = widget.nightMode.isDark(appContext)
            val icon = widget.coin.getIcon(widget.theme, isDark)
            widgetPresenter.setImageViewResource(R.id.icon, icon)
        }
    }

    protected fun updateAutoTextView(@IdRes viewId: Int, value: String, rectF: RectF) {
        getView(viewId).let {
            it.text = value
            val size = TextViewAutoSizeHelper.findLargestTextSizeWhichFits(it, rectF)
            widgetPresenter.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_PX, size.toFloat())
        }
    }

}

