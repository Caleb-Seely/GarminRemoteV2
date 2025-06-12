package com.garmin.android.apps.camera.click.comm.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo

class ButtonLocationOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = context.getColor(R.color.primary_dark)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        alpha = 180
    }

    private val textPaint = Paint().apply {
        color = context.getColor(R.color.primary_dark)
        textSize = 40f
        alpha = 180
        textAlign = Paint.Align.CENTER
    }

    private var buttonInfo: ShutterButtonInfo? = null
    private val actionBarHeight: Int

    init {
        val tv = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        } else {
            actionBarHeight = 0
        }
    }

    fun setButtonInfo(info: ShutterButtonInfo?) {
        buttonInfo = info
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        buttonInfo?.bounds?.let { bounds ->
            val adjustedBounds = Rect(bounds).apply {
                offset(0, -actionBarHeight)
            }
            
            val textY = adjustedBounds.top - 20f
            canvas.drawText("Shutter Location", adjustedBounds.centerX().toFloat(), textY, textPaint)
            canvas.drawRect(adjustedBounds, paint)
        }
    }
} 