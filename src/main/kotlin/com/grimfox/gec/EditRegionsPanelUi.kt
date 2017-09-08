package com.grimfox.gec

import com.grimfox.gec.brushes.PickAndGoDrawBrushListener
import com.grimfox.gec.brushes.SplineDeletePicker
import com.grimfox.gec.brushes.SplineDrawBrushListener
import com.grimfox.gec.brushes.SplinePointPicker
import com.grimfox.gec.model.ByteArrayMatrix
import com.grimfox.gec.model.ShortArrayMatrix
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.ui.JSON
import com.grimfox.gec.ui.UiLayout
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.RegionParameters
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.*

private val allowExperimental = ref(false)
private val regionsSeed = ref(1L)
private val edgeDetailScale = ref(4)
private val regions = ref(8)
private val islands = ref(1)
private val stride = ref(7)
private val startPoints = ref(2)
private val reduction = ref(7)
private val connectedness = ref(0.114f)
private val regionSize = ref(0.034f)
private val iterations = ref(5)
private val splineSmoothing = ref(10)
private val editSplinesSelectionRadius = ref(80)
private val deleteSplineSelectionRadius = ref(80)

private val onUpdateParamsFun = {
    if (!allowExperimental.value) {
        stride.value = ((regions.value + 3) / 4) + 5
        val stride2 = stride.value * stride.value
        val approxInland = stride2 - (4 * stride.value) + 4
        startPoints.value = Math.min(6, Math.max(1, Math.floor(approxInland / (regions.value.toDouble() + (islands.value / 2))).toInt() - 1))
        reduction.value = Math.floor((approxInland - (startPoints.value * regions.value)) * 0.8).toInt()
        connectedness.value = Math.round(((1.0f / stride.value) * 0.77f) * 1000.0f) / 1000.0f
        val unitLength = 1.0f / stride.value
        val unit2 = unitLength * unitLength
        regionSize.value = Math.round((((unit2 * (approxInland - reduction.value)) / regions.value) * 0.763f) * 1000.0f) / 1000.0f
    }
}

private val regionFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val useRegionFile = ref(false)
private val regionsBuilder = RegionsBuilder(regionFile, useRegionFile, displayMode, defaultToMap)

private fun syncParameterValues(parameters: RegionParameters) {
    val randomSeed = parameters.regionsSeed
    val randomString = randomSeed.toString()
    if (randomString.length > 18) {
        regionsSeed.value = randomString.substring(0, 18).toLong()
    } else {
        regionsSeed.value = randomSeed
    }
    edgeDetailScale.value = parameters.edgeDetailScale
    regions.value = parameters.regionCount
    stride.value = parameters.stride
    islands.value = parameters.islandDesire
    startPoints.value = parameters.regionPoints
    reduction.value = parameters.initialReduction
    connectedness.value = parameters.connectedness
    regionSize.value = parameters.regionSize
    iterations.value = parameters.maxRegionTries / 10
    iterations.value = parameters.maxIslandTries / 100
}

private fun extractCurrentParameters(): RegionParameters {
    return RegionParameters(
            regionsSeed = regionsSeed.value,
            edgeDetailScale = edgeDetailScale.value,
            regionCount = regions.value,
            stride = stride.value,
            islandDesire = islands.value,
            regionPoints = startPoints.value,
            initialReduction = reduction.value,
            connectedness = connectedness.value,
            regionSize = regionSize.value,
            maxRegionTries = iterations.value * 10,
            maxIslandTries = iterations.value * 100)
}

fun Block.editRegionsPanel(
        regionPanelExpanded: MonitoredReference<Boolean>,
        generationLock: DisableSetLock,
        editToggleSet: ToggleSet,
        leftPanelLabelShrinkGroup: ShrinkGroup,
        dialogLayer: Block,
        ui: UserInterface,
        uiLayout: UiLayout): Block {
    onUpdateParamsFun()
    regions.listener { _, _ -> onUpdateParamsFun() }
    islands.listener { _, _ -> onUpdateParamsFun() }
    return vExpandPanel("Edit regions", expanded = regionPanelExpanded) {
        vSpacer(HALF_ROW_HEIGHT)
        vButtonRow(LARGE_ROW_HEIGHT) {
            generationLock.disableOnLockButton(this, "Import regions") {
                generationLock.doWithLock {
                    val historyItem = openRegionsFile(dialogLayer, preferences, ui)
                    if (historyItem != null) {
                        val historyLast = historyRegionsCurrent.value
                        if (historyLast != null) {
                            historyRegionsBackQueue.push(historyLast.copy())
                        }
                        syncParameterValues(historyItem.parameters)
                        currentState.regionGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                        currentState.regionMask = historyItem.mask
                        regionsBuilder.build(historyItem.parameters, true, true)
                        historyRegionsCurrent.value = historyItem.copy()
                    }
                }
            }
            hSpacer(SMALL_SPACER_SIZE)
            generationLock.disableOnLockButton(this, "Export regions", { historyRegionsCurrent.value == null }) {
                generationLock.doWithLock {
                    exportRegionsFile(historyRegionsCurrent.value, dialogLayer, preferences, ui)
                }
            }
        }
        vSpacer(HALF_ROW_HEIGHT)
        vLongInputRow(regionsSeed, LARGE_ROW_HEIGHT, text("Seed:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, ui, uiLayout) {
            hSpacer(SMALL_SPACER_SIZE)
            button(text("Randomize"), NORMAL_TEXT_BUTTON_STYLE) {
                val randomSeed = RANDOM.nextLong()
                val randomString = randomSeed.toString()
                if (randomString.length > 18) {
                    regionsSeed.value = randomString.substring(0, 18).toLong()
                } else {
                    regionsSeed.value = randomSeed
                }
            }
        }
        vFileRowWithToggle(regionFile, useRegionFile, LARGE_ROW_HEIGHT, text("Region file:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
        val regionScaleSlider = vSliderWithValueRow(edgeDetailScale, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Edge detail:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20))
        var regionScaleSliderInitial = edgeDetailScale.value
        val regionScaleSliderMouseDown = regionScaleSlider.onMouseDown
        regionScaleSlider.onMouseDown { a, b, c, d ->
            regionScaleSliderMouseDown?.invoke(regionScaleSlider, a, b, c, d)
            regionScaleSliderInitial = edgeDetailScale.value
        }
        val regionScaleSliderMouseRelease = regionScaleSlider.onMouseRelease
        regionScaleSlider.onMouseRelease { a, b, c, d ->
            regionScaleSliderMouseRelease?.invoke(regionScaleSlider, a, b, c, d)
            executor.call {
                generationLock.doWithLock {
                    val currentGraph = currentState.regionGraph
                    val currentMask = currentState.regionMask
                    val currentSplines = currentState.regionSplines
                    if (edgeDetailScale.value != regionScaleSliderInitial && currentGraph != null && currentMask != null && currentSplines != null) {
                        val parameters = extractCurrentParameters()
                        regionsBuilder.build(parameters, true, true)
                        updateRegionsHistory(parameters, currentGraph, currentMask)
                        val currentRegionSplines = currentState.regionSplines
                        if (currentRegionSplines != null && displayMode.value == DisplayMode.MAP) {
                            val regionTextureId = TextureBuilder.renderMapImage(currentRegionSplines.coastPoints, currentRegionSplines.riverPoints + currentRegionSplines.customRiverPoints, currentRegionSplines.mountainPoints + currentRegionSplines.customMountainPoints, currentRegionSplines.ignoredPoints + currentRegionSplines.customIgnoredPoints)
                            meshViewport.setImage(regionTextureId)
                        }
                    }
                }
            }
        }
        vSliderWithValueRow(regions, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Number of regions:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..16), linearClampedScaleFunctionInverse(1..16))
        vSliderWithValueRow(islands, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Prefer islands:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..5), linearClampedScaleFunctionInverse(0..5))
        experimentalWidgets.add(vToggleRow(allowExperimental, LARGE_ROW_HEIGHT, text("Experimental:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE))
        val experimentalBlocks = ArrayList<Block>()
        experimentalBlocks.add(vSliderRow(iterations, LARGE_ROW_HEIGHT, text("Iterations:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(2..30), linearClampedScaleFunctionInverse(2..30)))
        experimentalBlocks.add(vSliderWithValueRow(stride, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Stride:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(5..10), linearClampedScaleFunctionInverse(5..10)).with { isMouseAware = allowExperimental.value })
        experimentalBlocks.add(vSliderWithValueRow(startPoints, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Start points:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..6), linearClampedScaleFunctionInverse(1..6)).with { isMouseAware = allowExperimental.value })
        experimentalBlocks.add(vSliderWithValueRow(reduction, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Reduction:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..40), linearClampedScaleFunctionInverse(0..40)).with { isMouseAware = allowExperimental.value })
        experimentalBlocks.add(vSliderWithValueRow(connectedness, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Connection:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.004f, 0.2f), linearClampedScaleFunctionInverse(0.004f, 0.2f)).with { isMouseAware = allowExperimental.value })
        experimentalBlocks.add(vSliderWithValueRow(regionSize, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Region size:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.005f, 0.05f), linearClampedScaleFunctionInverse(0.005f, 0.05f)).with { isMouseAware = allowExperimental.value })
        allowExperimental.listener { _, new ->
            if (!new) {
                onUpdateParamsFun()
            }
            experimentalBlocks.forEach {
                it.isMouseAware = new
                it.isVisible = new
            }
        }
        allowExperimental.value = false
        val editRegionsMode = ref(false)
        vToggleRow(editRegionsMode, LARGE_ROW_HEIGHT, text("Manual edit:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE)
        editToggleSet.add(editRegionsMode,
                {
                    val currentGraph = currentState.regionGraph
                    val currentMask = currentState.regionMask
                    if (currentGraph != null && currentMask != null) {
                        generationLock.lock()
                        val regionTextureId = Rendering.renderRegions(currentGraph, currentMask)
                        meshViewport.setRegions(regionTextureId)
                        imageMode.value = 0
                        displayMode.value = DisplayMode.REGIONS
                        defaultToMap.value = false
                        val textureReference = ref(TextureBuilder.TextureId(-1))
                        textureReference.listener { oldTexture, newTexture ->
                            if (oldTexture != newTexture) {
                                meshViewport.setRegions(newTexture)
                            }
                        }
                        currentEditBrushSize.value = regionEditBrushSize
                        brushListener.value = PickAndGoDrawBrushListener(currentGraph, currentMask, regionEditBrushSize, textureReference)
                        brushOn.value = true
                        true
                    } else {
                        false
                    }
                },
                {
                    brushListener.value = null
                    brushOn.value = false
                    val parameters = extractCurrentParameters()
                    regionsBuilder.build(parameters, true)
                    val currentRegionSplines = currentState.regionSplines
                    val currentGraph = currentState.regionGraph
                    val currentMask = currentState.regionMask
                    if (currentRegionSplines != null && currentGraph != null && currentMask != null) {
                        updateRegionsHistory(parameters, currentGraph, currentMask)
                        val mapTextureId = TextureBuilder.renderMapImage(currentRegionSplines.coastPoints, currentRegionSplines.riverPoints + currentRegionSplines.customRiverPoints, currentRegionSplines.mountainPoints + currentRegionSplines.customMountainPoints, currentRegionSplines.ignoredPoints + currentRegionSplines.customIgnoredPoints)
                        meshViewport.setImage(mapTextureId)
                        imageMode.value = 1
                        displayMode.value = DisplayMode.MAP
                        defaultToMap.value = true
                    }
                    generationLock.unlock()
                },
                vSliderRow(regionEditBrushSize, LARGE_ROW_HEIGHT, text("Brush size:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.015625f, 0.15625f), linearClampedScaleFunctionInverse(0.015625f, 0.15625f)))
        vSpacer(HALF_ROW_HEIGHT)
        vButtonRow(LARGE_ROW_HEIGHT) {
            generationLock.disableOnLockButton(this, "Generate") {
                generationLock.doWithLock {
                    val parameters = extractCurrentParameters()
                    regionsBuilder.build(parameters)
                    val currentGraph = currentState.regionGraph
                    val currentMask = currentState.regionMask
                    if (currentGraph != null && currentMask != null) {
                        updateRegionsHistory(parameters, currentGraph, currentMask)
                        val currentSplines = currentState.regionSplines
                        if (historySplinesCurrent.value == null && currentSplines != null) {
                            updateSplinesHistory(currentSplines)
                        }
                    }
                }
            }
            hSpacer(SMALL_SPACER_SIZE)
            generationLock.disableOnLockButton(this, "Generate random") {
                generationLock.doWithLock {
                    val randomSeed = RANDOM.nextLong()
                    val randomString = randomSeed.toString()
                    if (randomString.length > 18) {
                        regionsSeed.value = randomString.substring(0, 18).toLong()
                    } else {
                        regionsSeed.value = randomSeed
                    }
                    val parameters = extractCurrentParameters()
                    regionsBuilder.build(parameters)
                    val currentGraph = currentState.regionGraph
                    val currentMask = currentState.regionMask
                    if (currentGraph != null && currentMask != null) {
                        updateRegionsHistory(parameters, currentGraph, currentMask)
                        val currentSplines = currentState.regionSplines
                        if (historySplinesCurrent.value == null && currentSplines != null) {
                            updateSplinesHistory(currentSplines)
                        }
                    }
                }
            }
            hSpacer(SMALL_SPACER_SIZE)
            generationLock.disableOnLockButton(this, "Back", { historyRegionsBackQueue.size == 0 }) {
                generationLock.doWithLock {
                    val historyItem = historyRegionsBackQueue.pop()
                    if (historyItem != null) {
                        val historyLast = historyRegionsCurrent.value
                        if (historyLast != null) {
                            historyRegionsForwardQueue.push(historyLast.copy())
                        }
                        syncParameterValues(historyItem.parameters)
                        currentState.regionGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                        currentState.regionMask = historyItem.mask
                        regionsBuilder.build(historyItem.parameters, true, true)
                        historyRegionsCurrent.value = historyItem.copy()
                        val currentSplines = currentState.regionSplines
                        if (historySplinesCurrent.value == null && currentSplines != null) {
                            updateSplinesHistory(currentSplines)
                        }
                    }
                }
            }
            hSpacer(SMALL_SPACER_SIZE)
            generationLock.disableOnLockButton(this, "Forward", { historyRegionsForwardQueue.size == 0 }) {
                generationLock.doWithLock {
                    val historyItem = historyRegionsForwardQueue.pop()
                    if (historyItem != null) {
                        val historyLast = historyRegionsCurrent.value
                        if (historyLast != null) {
                            historyRegionsBackQueue.push(historyLast.copy())
                        }
                        syncParameterValues(historyItem.parameters)
                        currentState.regionGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                        currentState.regionMask = historyItem.mask
                        regionsBuilder.build(historyItem.parameters, true, true)
                        historyRegionsCurrent.value = historyItem.copy()
                        val currentSplines = currentState.regionSplines
                        if (historySplinesCurrent.value == null && currentSplines != null) {
                            updateSplinesHistory(currentSplines)
                        }
                    }
                }
            }
        }
        vSpacer(HALF_ROW_HEIGHT)
    }
}

fun Block.editMapPanel(
        splinePanelExpanded: MonitoredReference<Boolean>,
        generationLock: DisableSetLock,
        editToggleSet: ToggleSet,
        leftPanelLabelShrinkGroup: ShrinkGroup): Block {
    val splinePanel = vExpandPanel("Edit splines", expanded = splinePanelExpanded) {
        val drawSplinesMode = ref(false)
        vToggleRow(drawSplinesMode, LARGE_ROW_HEIGHT, text("Draw splines:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE)
        editToggleSet.add(drawSplinesMode,
                {
                    val currentSplines = currentState.regionSplines
                    if (currentSplines != null) {
                        generationLock.lock()
                        val splineMap = LinkedHashMap<Int, Quadruple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>>>()
                        var index = 0
                        currentSplines.riverOrigins.zip(currentSplines.riverPoints).zip(currentSplines.riverEdges).forEach { (pair, edges) ->
                            index++
                            splineMap.put(index, Quadruple(index, 0, pair, edges))
                        }
                        currentSplines.mountainOrigins.zip(currentSplines.mountainPoints).zip(currentSplines.mountainEdges).forEach { (pair, edges) ->
                            index++
                            splineMap.put(index, Quadruple(index, 1, pair, edges))
                        }
                        currentSplines.ignoredOrigins.zip(currentSplines.ignoredPoints).zip(currentSplines.ignoredEdges).forEach { (pair, edges) ->
                            index++
                            splineMap.put(index, Quadruple(index, 2, pair, edges))
                        }
                        currentSplines.customRiverPoints.zip(currentSplines.customRiverEdges).forEach { (points, edges) ->
                            index++
                            splineMap.put(index, Quadruple(index, 3, points to points, edges))
                        }
                        currentSplines.customMountainPoints.zip(currentSplines.customMountainEdges).forEach { (points, edges) ->
                            index++
                            splineMap.put(index, Quadruple(index, 4, points to points, edges))
                        }
                        currentSplines.customIgnoredPoints.zip(currentSplines.customIgnoredEdges).forEach { (points, edges) ->
                            index++
                            splineMap.put(index, Quadruple(index, 5, points to points, edges))
                        }
                        if (displayMode.value != DisplayMode.MAP) {
                            val mapTextureId = TextureBuilder.renderMapImage(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints, currentSplines.ignoredPoints + currentSplines.customIgnoredPoints)
                            meshViewport.setImage(mapTextureId)
                            imageMode.value = 1
                            displayMode.value = DisplayMode.MAP
                            defaultToMap.value = true
                        }
                        val textureReference = ref(TextureBuilder.TextureId(-1))
                        textureReference.listener { oldTexture, newTexture ->
                            if (oldTexture != newTexture) {
                                meshViewport.setImage(newTexture)
                            }
                        }
                        currentEditBrushSize.value = drawSplineBrushSize
                        brushListener.value = SplineDrawBrushListener(splineSmoothing, currentState, currentSplines, splineMap, textureReference)
                        brushOn.value = true
                        true
                    } else {
                        false
                    }
                },
                {
                    brushListener.value = null
                    brushOn.value = false
                    val parameters = extractCurrentParameters()
                    regionsBuilder.build(parameters, true, false)
                    val currentSplines = currentState.regionSplines
                    if (currentSplines != null) {
                        updateSplinesHistory(currentSplines)
                    }
                    generationLock.unlock()
                },
                vSliderRow(splineSmoothing, LARGE_ROW_HEIGHT, text("Smoothing:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20)))
        val editSplinesMode = ref(false)
        vToggleRow(editSplinesMode, LARGE_ROW_HEIGHT, text("Toggle splines:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE)
        val splineEditRadiusSlider = vSliderRow(editSplinesSelectionRadius, LARGE_ROW_HEIGHT, text("Selection radius:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(10..500), linearClampedScaleFunctionInverse(10..500))
        var splineEditRadiusSliderInitial = editSplinesSelectionRadius.value
        val splineEditRadiusSliderMouseDown = splineEditRadiusSlider.onMouseDown
        splineEditRadiusSlider.onMouseDown { a, b, c, d ->
            splineEditRadiusSliderMouseDown?.invoke(splineEditRadiusSlider, a, b, c, d)
            splineEditRadiusSliderInitial = editSplinesSelectionRadius.value
        }
        var splineEditSelectorMap: LinkedHashMap<Int, Quadruple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>>>? = null
        var splineEditSelectorMatrix: ShortArrayMatrix? = null
        val splineEditRadiusSliderMouseRelease = splineEditRadiusSlider.onMouseRelease
        splineEditRadiusSlider.onMouseRelease { a, b, c, d ->
            splineEditRadiusSliderMouseRelease?.invoke(splineEditRadiusSlider, a, b, c, d)
            executor.call {
                val splineMap = splineEditSelectorMap
                val selectorMatrix = splineEditSelectorMatrix
                if (editSplinesSelectionRadius.value != splineEditRadiusSliderInitial && selectorMatrix != null && splineMap != null) {
                    val splineSelectors = TextureBuilder.extractTextureRgbaByte(TextureBuilder.renderSplineSelectors(splineMap.values.map { it.first to it.third.second }, editSplinesSelectionRadius.value.toFloat()), 4096)
                    for (i in 0..16777215) {
                        var offset = i * 4
                        val r = splineSelectors[offset++].toInt() and 0x000000FF
                        val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                        selectorMatrix[i] = (r or g).toShort()
                    }
                }
            }
        }
        editToggleSet.add(editSplinesMode,
                {
                    val currentSplines = currentState.regionSplines
                    if (currentSplines != null) {
                        generationLock.lock()
                        val splineMap = LinkedHashMap<Int, Quadruple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>>>()
                        var index = 0
                        currentSplines.riverOrigins.zip(currentSplines.riverPoints).zip(currentSplines.riverEdges).forEach { (pair, edges) ->
                            index++
                            splineMap.put(index, Quadruple(index, 0, pair, edges))
                        }
                        currentSplines.mountainOrigins.zip(currentSplines.mountainPoints).zip(currentSplines.mountainEdges).forEach { (pair, edges) ->
                            index++
                            splineMap.put(index, Quadruple(index, 1, pair, edges))
                        }
                        currentSplines.ignoredOrigins.zip(currentSplines.ignoredPoints).zip(currentSplines.ignoredEdges).forEach { (pair, edges) ->
                            index++
                            splineMap.put(index, Quadruple(index, 2, pair, edges))
                        }
                        currentSplines.customRiverPoints.zip(currentSplines.customRiverEdges).forEach { (points, edges) ->
                            index++
                            splineMap.put(index, Quadruple(index, 3, points to points, edges))
                        }
                        currentSplines.customMountainPoints.zip(currentSplines.customMountainEdges).forEach { (points, edges) ->
                            index++
                            splineMap.put(index, Quadruple(index, 4, points to points, edges))
                        }
                        currentSplines.customIgnoredPoints.zip(currentSplines.customIgnoredEdges).forEach { (points, edges) ->
                            index++
                            splineMap.put(index, Quadruple(index, 5, points to points, edges))
                        }
                        splineEditSelectorMap = splineMap
                        if (displayMode.value != DisplayMode.MAP) {
                            val mapTextureId = TextureBuilder.renderMapImage(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints, currentSplines.ignoredPoints + currentSplines.customIgnoredPoints)
                            meshViewport.setImage(mapTextureId)
                            imageMode.value = 1
                            displayMode.value = DisplayMode.MAP
                            defaultToMap.value = true
                        }
                        val textureReference = ref(TextureBuilder.TextureId(-1))
                        textureReference.listener { oldTexture, newTexture ->
                            if (oldTexture != newTexture) {
                                meshViewport.setImage(newTexture)
                            }
                        }
                        val splineSelectors = TextureBuilder.extractTextureRgbaByte(TextureBuilder.renderSplineSelectors(splineMap.values.map { it.first to it.third.second }, editSplinesSelectionRadius.value.toFloat()), 4096)
                        val selectorMatrix = ShortArrayMatrix(4096) { i ->
                            var offset = i * 4
                            val r = splineSelectors[offset++].toInt() and 0x000000FF
                            val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                            (r or g).toShort()
                        }
                        splineEditSelectorMatrix = selectorMatrix
                        pointPicker.value = SplinePointPicker(currentState, currentSplines, splineMap, selectorMatrix, textureReference)
                        pickerOn.value = true
                        true
                    } else {
                        false
                    }
                },
                {
                    splineEditSelectorMatrix = null
                    pointPicker.value = null
                    pickerOn.value = false
                    val parameters = extractCurrentParameters()
                    regionsBuilder.build(parameters, true, false)
                    val currentSplines = currentState.regionSplines
                    if (currentSplines != null) {
                        updateSplinesHistory(currentSplines)
                    }
                    generationLock.unlock()
                },
                splineEditRadiusSlider)
        val deleteSplinesMode = ref(false)
        vToggleRow(deleteSplinesMode, LARGE_ROW_HEIGHT, text("Delete splines:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE)
        val splineDeleteRadiusSlider = vSliderRow(deleteSplineSelectionRadius, LARGE_ROW_HEIGHT, text("Selection radius:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(10..500), linearClampedScaleFunctionInverse(10..500))
        var splineDeleteRadiusSliderInitial = deleteSplineSelectionRadius.value
        val splineDeleteRadiusSliderMouseDown = splineDeleteRadiusSlider.onMouseDown
        splineDeleteRadiusSlider.onMouseDown { a, b, c, d ->
            splineDeleteRadiusSliderMouseDown?.invoke(splineDeleteRadiusSlider, a, b, c, d)
            splineDeleteRadiusSliderInitial = deleteSplineSelectionRadius.value
        }
        var splineDeleteSelectorMap: LinkedHashMap<Int, Quintuple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>, Boolean>>? = null
        var splineDeleteSelectorMatrix: ShortArrayMatrix? = null
        val splineDeleteRadiusSliderMouseRelease = splineDeleteRadiusSlider.onMouseRelease
        splineDeleteRadiusSlider.onMouseRelease { a, b, c, d ->
            splineDeleteRadiusSliderMouseRelease?.invoke(splineDeleteRadiusSlider, a, b, c, d)
            executor.call {
                val splineMap = splineDeleteSelectorMap
                val selectorMatrix = splineDeleteSelectorMatrix
                if (deleteSplineSelectionRadius.value != splineDeleteRadiusSliderInitial && selectorMatrix != null && splineMap != null) {
                    val splineSelectors = TextureBuilder.extractTextureRgbaByte(TextureBuilder.renderSplineSelectors(splineMap.values.map { it.first to it.third.second }, deleteSplineSelectionRadius.value.toFloat()), 4096)
                    for (i in 0..16777215) {
                        var offset = i * 4
                        val r = splineSelectors[offset++].toInt() and 0x000000FF
                        val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                        selectorMatrix[i] = (r or g).toShort()
                    }
                }
            }
        }
        editToggleSet.add(deleteSplinesMode,
                {
                    val currentSplines = currentState.regionSplines
                    if (currentSplines != null) {
                        generationLock.lock()
                        val splineMap = LinkedHashMap<Int, Quintuple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>, Boolean>>()
                        var index = 0
                        currentSplines.riverOrigins.zip(currentSplines.riverPoints).zip(currentSplines.riverEdges).forEach { (pair, edges) ->
                            index++
                            splineMap.put(index, Quintuple(index, 0, pair, edges, false))
                        }
                        currentSplines.mountainOrigins.zip(currentSplines.mountainPoints).zip(currentSplines.mountainEdges).forEach { (pair, edges) ->
                            index++
                            splineMap.put(index, Quintuple(index, 1, pair, edges, false))
                        }
                        currentSplines.ignoredOrigins.zip(currentSplines.ignoredPoints).zip(currentSplines.ignoredEdges).forEach { (pair, edges) ->
                            index++
                            splineMap.put(index, Quintuple(index, 2, pair, edges, false))
                        }
                        currentSplines.deletedOrigins.zip(currentSplines.deletedPoints).zip(currentSplines.deletedEdges).forEach { (pair, edges) ->
                            index++
                            splineMap.put(index, Quintuple(index, 2, pair, edges, true))
                        }
                        currentSplines.customRiverPoints.zip(currentSplines.customRiverEdges).forEach { (points, edges) ->
                            index++
                            splineMap.put(index, Quintuple(index, 3, points to points, edges, false))
                        }
                        currentSplines.customMountainPoints.zip(currentSplines.customMountainEdges).forEach { (points, edges) ->
                            index++
                            splineMap.put(index, Quintuple(index, 4, points to points, edges, false))
                        }
                        currentSplines.customIgnoredPoints.zip(currentSplines.customIgnoredEdges).forEach { (points, edges) ->
                            index++
                            splineMap.put(index, Quintuple(index, 5, points to points, edges, false))
                        }
                        splineDeleteSelectorMap = splineMap
                        if (displayMode.value != DisplayMode.MAP) {
                            val mapTextureId = TextureBuilder.renderMapImage(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints, currentSplines.ignoredPoints + currentSplines.customIgnoredPoints)
                            meshViewport.setImage(mapTextureId)
                            imageMode.value = 1
                            displayMode.value = DisplayMode.MAP
                            defaultToMap.value = true
                        }
                        val textureReference = ref(TextureBuilder.TextureId(-1))
                        textureReference.listener { oldTexture, newTexture ->
                            if (oldTexture != newTexture) {
                                meshViewport.setImage(newTexture)
                            }
                        }
                        executor.call {
                            textureReference.value = TextureBuilder.renderMapImage(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints, currentSplines.ignoredPoints + currentSplines.customIgnoredPoints, currentSplines.deletedPoints)
                        }
                        val splineSelectors = TextureBuilder.extractTextureRgbaByte(TextureBuilder.renderSplineSelectors(splineMap.values.map { it.first to it.third.second }, 80.0f), 4096)
                        val selectorMatrix = ShortArrayMatrix(4096) { i ->
                            var offset = i * 4
                            val r = splineSelectors[offset++].toInt() and 0x000000FF
                            val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                            (r or g).toShort()
                        }
                        splineDeleteSelectorMatrix = selectorMatrix
                        pointPicker.value = SplineDeletePicker(currentState, currentSplines, splineMap, selectorMatrix, textureReference)
                        pickerOn.value = true
                        true
                    } else {
                        false
                    }
                },
                {
                    splineDeleteSelectorMatrix = null
                    pointPicker.value = null
                    pickerOn.value = false
                    val parameters = extractCurrentParameters()
                    regionsBuilder.build(parameters, true, false)
                    val currentSplines = currentState.regionSplines
                    if (currentSplines != null) {
                        updateSplinesHistory(currentSplines)
                    }
                    generationLock.unlock()
                },
                splineDeleteRadiusSlider)
        vButtonRow(LARGE_ROW_HEIGHT) {
            generationLock.disableOnLockButton(this, "Clear edits", { !(currentState.regionSplines?.hasCustomizations ?: false) }) {
                generationLock.doWithLock {
                    val parameters = extractCurrentParameters()
                    currentState.regionSplines = null
                    regionsBuilder.build(parameters, true, true)
                    val currentSplines = currentState.regionSplines
                    if (currentSplines != null) {
                        updateSplinesHistory(currentSplines)
                    }
                }
            }
            hSpacer(SMALL_SPACER_SIZE)
            generationLock.disableOnLockButton(this, "Restore deleted", { currentState.regionSplines?.deletedOrigins?.isEmpty() ?: true }) {
                generationLock.doWithLock {
                    val parameters = extractCurrentParameters()
                    currentState.regionSplines = currentState.regionSplines?.copy(deletedOrigins = listOf(), deletedPoints = listOf(), deletedEdges = listOf())
                    regionsBuilder.build(parameters, true, true)
                    val currentSplines = currentState.regionSplines
                    if (currentSplines != null) {
                        updateSplinesHistory(currentSplines)
                    }
                }
            }
            hSpacer(SMALL_SPACER_SIZE)
            generationLock.disableOnLockButton(this, "Back", { historySplinesBackQueue.size == 0 }) {
                generationLock.doWithLock {
                    val historyItem = historySplinesBackQueue.pop()
                    if (historyItem != null) {
                        val historyLast = historySplinesCurrent.value
                        if (historyLast != null) {
                            historySplinesForwardQueue.push(historyLast.copy())
                        }
                        currentState.regionSplines = historyItem
                        val parameters = currentState.parameters
                        if (parameters != null) {
                            regionsBuilder.build(parameters, true, true)
                        }
                        historySplinesCurrent.value = historyItem.copy()
                    }
                }
            }
            hSpacer(SMALL_SPACER_SIZE)
            generationLock.disableOnLockButton(this, "Forward", { historySplinesForwardQueue.size == 0 }) {
                generationLock.doWithLock {
                    val historyItem = historySplinesForwardQueue.pop()
                    if (historyItem != null) {
                        val historyLast = historySplinesCurrent.value
                        if (historyLast != null) {
                            historySplinesBackQueue.push(historyLast.copy())
                        }
                        currentState.regionSplines = historyItem
                        val parameters = currentState.parameters
                        if (parameters != null) {
                            regionsBuilder.build(parameters, true, true)
                        }
                        historySplinesCurrent.value = historyItem.copy()
                    }
                }
            }
        }
        vSpacer(HALF_ROW_HEIGHT)
    }
    return splinePanel
}

private fun openRegionsFile(dialogLayer: Block, preferences: Preferences, ui: UserInterface): RegionsHistoryItem? {
    return FileDialogs.selectFile(dialogLayer, true, ui, preferences.projectDir, "wkr") { file ->
        if (file == null) {
            null
        } else {
            val historyItem = DataInputStream(file.inputStream().buffered()).use { stream ->
                val parameters = JSON.readValue(stream.readUTF(), RegionParameters::class.java)
                val graphSeed = stream.readLong()
                val maskWidth = stream.readInt()
                val maskBytes = ByteArray(maskWidth * maskWidth)
                stream.readFully(maskBytes)
                val regionMask = ByteArrayMatrix(maskWidth, maskBytes)
                RegionsHistoryItem(parameters, graphSeed, regionMask)
            }
            historyItem
        }
    }
}

private fun exportRegionsFile(regions: RegionsHistoryItem?, dialogLayer: Block, preferences: Preferences, ui: UserInterface): Boolean {
    if (regions != null) {
        ui.ignoreInput = true
        dialogLayer.isVisible = true
        try {
            val saveFile = FileDialogs.saveFileDialog(preferences.projectDir, "wkr")
            if (saveFile != null) {
                val fullNameWithExtension = "${saveFile.name.removeSuffix(".wkr")}.wkr"
                val actualFile = File(saveFile.parentFile, fullNameWithExtension)
                DataOutputStream(actualFile.outputStream().buffered()).use { stream ->
                    val parameters = JSON.writeValueAsString(regions.parameters)
                    stream.writeUTF(parameters)
                    stream.writeLong(regions.graphSeed)
                    stream.writeInt(regions.mask.width)
                    stream.write(regions.mask.array)
                }
                return true
            } else {
                return false
            }
        } finally {
            dialogLayer.isVisible = false
            ui.ignoreInput = false
        }
    }
    return false
}