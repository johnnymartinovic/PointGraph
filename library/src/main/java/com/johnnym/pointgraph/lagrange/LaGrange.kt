package com.johnnym.pointgraph.lagrange

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.johnnym.pointgraph.PointsData
import com.johnnym.pointgraph.utils.*
import kotlin.properties.Delegates

/**
 * View that enables the user to select a subrange of a range defined by the
 * [PointsData]'s minimum and maximum values, while having a graph presentation
 * of [Point]s defined in the same [PointsData] object.
 */
class LaGrange @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val minSelectorAnimator = ValueAnimator().apply {
        duration = 150
        addUpdateListener { animation ->
            drawObjects.updateMinSelectorDependantShapes(animation.animatedValue as Float)
            invalidate()
        }
    }
    private val maxSelectorAnimator = ValueAnimator().apply {
        duration = 150
        addUpdateListener { animation ->
            drawObjects.updateMaxSelectorDependantShapes(animation.animatedValue as Float)
            invalidate()
        }
    }
    private val graphScaleAnimator = ValueAnimator().apply {
        duration = 300
        setFloatValues(GRAPH_Y_AXIS_SCALE_FACTOR_MIN_VALUE, GRAPH_Y_AXIS_SCALE_FACTOR_MAX_VALUE)
        addUpdateListener { animation ->
            graphYAxisScaleFactor = animation.animatedValue as Float
            invalidate()
        }
    }

    private var graphYAxisScaleFactor = GRAPH_Y_AXIS_SCALE_FACTOR_MAX_VALUE

    private val touchHandlerListener = object : LaGrangeTouchHandler.Listener {

        override fun isInMinSelectorTouchField(x: Float, y: Float): Boolean =
                drawObjects.isInMinSelectorTouchField(x, y)

        override fun isInMaxSelectorTouchField(x: Float, y: Float): Boolean =
                drawObjects.isInMaxSelectorTouchField(x, y)

        override fun minSelectorChanged(xPosition: Float) {
            val newXPosition: Float = constrainToRange(xPosition, dimensions.graphLeft, drawObjects.getMaxSelectorXPosition())

            pointsData?.let {
                minSelectorValue = transformSelectorXPositionToValue(it, newXPosition)
            }

            drawObjects.updateMinSelectorDependantShapes(newXPosition)
            invalidate()
        }

        override fun maxSelectorChanged(xPosition: Float) {
            val newXPosition: Float = constrainToRange(xPosition, drawObjects.getMinSelectorXPosition(), dimensions.graphRight)

            pointsData?.let {
                maxSelectorValue = transformSelectorXPositionToValue(it, newXPosition)
            }

            drawObjects.updateMaxSelectorDependantShapes(newXPosition)
            invalidate()
        }
    }

    private val attributes: LaGrangeAttrs = LaGrangeAttrs.create(context, attrs, defStyleAttr)
    private val dimensions: LaGrangeDimensions = LaGrangeDimensions(attributes)
    private val drawObjects: LaGrangeDraw = LaGrangeDraw(attributes, dimensions)
    private val touchHandler: LaGrangeTouchHandler = LaGrangeTouchHandler(touchHandlerListener)

    private val minSelectorPositionChangeListeners = ArrayList<LaGrange.MinSelectorPositionChangeListener>()
    private val maxSelectorPositionChangeListeners = ArrayList<LaGrange.MaxSelectorPositionChangeListener>()
    private var pointsData: PointsData? = null
    private var listenersEnabled = true

    /**
     * True min selector value (set from outside or by touch events)
     */
    var minSelectorValue: Float by Delegates.observable(0f) { _, _, new: Float ->
        if (listenersEnabled) {
            minSelectorPositionChangeListeners.dispatchOnMinSelectorPositionChangeEvent(new)
        }
    }
        private set

    /**
     * True max selector value (set from outside or by touch events)
     */
    var maxSelectorValue: Float by Delegates.observable(0f) { _, _, new: Float ->
        if (listenersEnabled) {
            maxSelectorPositionChangeListeners.dispatchOnMaxSelectorPositionChangeEvent(new)
        }
    }
        private set

    /**
     * Set [LaGrange] graph data
     *
     * @param pointsData [Point]s to be shown in form of a graph
     * @param animated true if this set of data should be animated
     */
    fun setPointsData(pointsData: PointsData?, animated: Boolean = true) {
        this.pointsData = pointsData
        if (pointsData != null) {
            this.minSelectorValue = pointsData.minX
            this.maxSelectorValue = pointsData.maxX
        }

        resetDataAndSelectorDrawObjects()

        if (animated) {
            graphScaleAnimator.start()
        } else {
            invalidate()
        }
    }

    /**
     * Set [minSelectorValue] and [maxSelectorValue] values
     *
     * Values will be normalized to fit the current [PointsData] value in [pointsData].
     * Also, if [maxValue] is smaller than [minValue], its value will be changed to [minValue].
     *
     * @param minValue wanted [minSelectorValue]
     * @param maxValue wanted [maxSelectorValue]
     */
    fun setSelectorsValues(minValue: Float?, maxValue: Float?, animated: Boolean) {
        // if user is interacting with the view, do not set values from outside
        if (touchHandler.isAnySelectorSelected()) {
            return
        }

        this.pointsData?.let { pointsData ->
            normalizeAndSetSelectorsValues(pointsData, minValue, maxValue)

            moveMinSelectorXPosition(
                    transformSelectorValueToXPosition(pointsData, minSelectorValue),
                    animated)

            moveMaxSelectorXPosition(
                    transformSelectorValueToXPosition(pointsData, maxSelectorValue),
                    animated)
        }
    }

    private fun normalizeAndSetSelectorsValues(pointsData: PointsData, minValue: Float?, maxValue: Float?) {
        var minSelectorValue = minValue ?: pointsData.minX
        var maxSelectorValue = maxValue ?: pointsData.maxX

        minSelectorValue = Math.max(minSelectorValue, pointsData.minX)
        minSelectorValue = Math.min(minSelectorValue, pointsData.maxX)
        maxSelectorValue = Math.max(maxSelectorValue, pointsData.minX)
        maxSelectorValue = Math.min(maxSelectorValue, pointsData.maxX)

        if (minSelectorValue > maxSelectorValue) {
            maxSelectorValue = minSelectorValue
        }

        this.minSelectorValue = minSelectorValue
        this.maxSelectorValue = maxSelectorValue
    }

    /**
     * Add a listener which will be informed about the changes of [minSelectorValue].
     *
     * @param minSelectorPositionChangeListener listener to be added
     */
    fun addMinSelectorChangeListener(minSelectorPositionChangeListener: MinSelectorPositionChangeListener) {
        this.minSelectorPositionChangeListeners.add(minSelectorPositionChangeListener)
    }

    /**
     * Remove a listener which previously has been informed about the changes of [minSelectorValue].
     *
     * @param minSelectorPositionChangeListener listener to be removed
     */
    fun removeMinSelectorChangeListener(minSelectorPositionChangeListener: MinSelectorPositionChangeListener) {
        this.minSelectorPositionChangeListeners.remove(minSelectorPositionChangeListener)
    }

    /**
     * Add a listener which will be informed about the changes of [maxSelectorValue].
     *
     * @param maxSelectorPositionChangeListener listener to be added
     */
    fun addMaxSelectorChangeListener(maxSelectorPositionChangeListener: MaxSelectorPositionChangeListener) {
        this.maxSelectorPositionChangeListeners.add(maxSelectorPositionChangeListener)
    }

    /**
     * Remove a listener which previously has been informed about the changes of [maxSelectorValue].
     *
     * @param maxSelectorPositionChangeListener listener to be removed
     */
    fun removeMaxSelectorChangeListener(maxSelectorPositionChangeListener: MaxSelectorPositionChangeListener) {
        this.maxSelectorPositionChangeListeners.remove(maxSelectorPositionChangeListener)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSizeAndState(attributes.minViewWidth.toInt(), widthMeasureSpec, 0)
        val height = resolveSizeAndState(attributes.minViewHeight.toInt(), heightMeasureSpec, 0)

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        dimensions.updateDimensions(
                paddingLeft,
                w - paddingRight,
                paddingTop,
                h - paddingBottom)
        drawObjects.refreshStaticObjects()
        resetDataAndSelectorDrawObjects()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        this.pointsData
                ?.let {
                    touchHandler.handleTouchEvent(event)

                    // If any of the selectors is selected, then user must be able to move his finger anywhere
                    // on the screen and still have control of the selected selector.
                    if (touchHandler.isAnySelectorSelected()) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                    return true
                }
                ?: return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        this.pointsData
                ?.let { drawObjects.drawWithData(canvas, graphYAxisScaleFactor) }
                ?: drawObjects.drawWithoutData(canvas)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.pointsData = this.pointsData
        savedState.minSelectorValue = this.minSelectorValue
        savedState.maxSelectorValue = this.maxSelectorValue
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        this.listenersEnabled = false
        this.pointsData = state.pointsData
        this.minSelectorValue = state.minSelectorValue
        this.maxSelectorValue = state.maxSelectorValue
        resetDataAndSelectorDrawObjects()
        this.listenersEnabled = true

        super.onRestoreInstanceState(state.superState)
    }

    private fun moveMinSelectorXPosition(x: Float, animated: Boolean = false) {
        if (animated) {
            minSelectorAnimator.setFloatValues(drawObjects.getMinSelectorXPosition(), x)
            minSelectorAnimator.start()
        } else {
            drawObjects.updateMinSelectorDependantShapes(x)
            invalidate()
        }
    }

    private fun moveMaxSelectorXPosition(x: Float, animated: Boolean = false) {
        if (animated) {
            maxSelectorAnimator.setFloatValues(drawObjects.getMaxSelectorXPosition(), x)
            maxSelectorAnimator.start()
        } else {
            drawObjects.updateMaxSelectorDependantShapes(x)
            invalidate()
        }
    }

    private fun resetDataAndSelectorDrawObjects() {
        pointsData?.let {
            drawObjects.refreshDataObjects(it)
            drawObjects.updateMinSelectorDependantShapes(transformSelectorValueToXPosition(it, this.minSelectorValue))
            drawObjects.updateMaxSelectorDependantShapes(transformSelectorValueToXPosition(it, this.maxSelectorValue))
        }
    }

    private fun transformSelectorXPositionToValue(pointsData: PointsData, selectorXPosition: Float): Float =
            affineTransformXToY(selectorXPosition, dimensions.graphLeft, dimensions.graphRight, pointsData.minX, pointsData.maxX)

    private fun transformSelectorValueToXPosition(pointsData: PointsData, selectorValue: Float): Float =
            affineTransformXToY(selectorValue, pointsData.minX, pointsData.maxX, dimensions.graphLeft, dimensions.graphRight)

    /**
     * Listener interface whose methods are called as a consequence of
     * [minSelectorValue] value change events.
     */
    interface MinSelectorPositionChangeListener {

        /**
         * Called when [minSelectorValue] is changed.
         *
         * @param newMinValue [minSelectorValue] new value
         */
        fun onMinValueChanged(newMinValue: Float)
    }

    /**
     * Listener interface whose methods are called as a consequence of
     * [minSelectorValue] value change events.
     */
    interface MaxSelectorPositionChangeListener {

        /**
         * Called when [maxSelectorValue] is changed.
         *
         * @param newMaxValue [maxSelectorValue] new value
         */
        fun onMaxValueChanged(newMaxValue: Float)
    }
}