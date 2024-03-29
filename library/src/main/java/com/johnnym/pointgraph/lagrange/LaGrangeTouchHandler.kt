package com.johnnym.pointgraph.lagrange

import android.view.MotionEvent

class LaGrangeTouchHandler(
        private val listener: Listener) {

    interface Listener {

        fun isInMinSelector(x: Float, y: Float): Boolean

        fun isInMaxSelector(x: Float, y: Float): Boolean

        fun minSelectorChanged(xPosition: Float)

        fun maxSelectorChanged(xPosition: Float)
    }

    private var actionDownXValue: Float = 0f
    private var actionDownYValue: Float = 0f
    private var actionMoveXValue: Float = 0f
    private var minSelectorSelected: Boolean = false
    private var maxSelectorSelected: Boolean = false
    private var bothSelectorsSelected: Boolean = false

    fun handleTouchEvent(event: MotionEvent) {
        val action = event.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                actionDownXValue = event.x
                actionDownYValue = event.y

                val minSelectorContainsTouch = listener.isInMinSelector(
                        actionDownXValue, actionDownYValue)
                val maxSelectorContainsTouch = listener.isInMaxSelector(
                        actionDownXValue, actionDownYValue)

                if (minSelectorContainsTouch && maxSelectorContainsTouch) {
                    bothSelectorsSelected = true
                } else if (minSelectorContainsTouch) {
                    minSelectorSelected = true
                } else if (maxSelectorContainsTouch) {
                    maxSelectorSelected = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                actionMoveXValue = event.x
                if (bothSelectorsSelected) {
                    if (actionMoveXValue < actionDownXValue) {
                        bothSelectorsSelected = false
                        minSelectorSelected = true
                    } else if (actionMoveXValue > actionDownXValue) {
                        bothSelectorsSelected = false
                        maxSelectorSelected = true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                bothSelectorsSelected = false
                minSelectorSelected = false
                maxSelectorSelected = false
            }
        }

        val newXPosition = event.x
        if (minSelectorSelected) {
            listener.minSelectorChanged(newXPosition)
        } else if (maxSelectorSelected) {
            listener.maxSelectorChanged(newXPosition)
        }
    }

    fun isAnySelectorSelected() = bothSelectorsSelected || minSelectorSelected || maxSelectorSelected
}