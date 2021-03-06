package com.grimfox.gec

import com.grimfox.gec.ui.*
import com.grimfox.gec.ui.nvgproxy.set
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.*
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.*
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.RegionParameters
import com.grimfox.gec.util.BuildContinent.generateRegions
import com.grimfox.logging.LOG
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.util.*

object MainUi {

    @Volatile private var uiIsShown: Boolean = false

    @JvmStatic fun mainUi(vararg args: String) {
        if (args.isNotEmpty()) {
            val stringBuilder = StringBuilder("Command line args:\n\n")
            args.forEachIndexed { i, it ->
                stringBuilder.append("    arg${i + 1}: $it\n")
            }
            stringBuilder.append("\n")
            LOG.info(stringBuilder.toString())
        } else {
            LOG.info("No command line args")
        }
        val uiThread = Thread({ runUi(*args) })
        uiThread.isDaemon = false
        uiThread.start()
        for (i in 1..2) {
            task { generateRegions(RegionParameters(regionsSeed = i.toLong()), executor) }
        }
        while (!uiIsShown) {
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun runUi(vararg args: String) {
        val preferences = preferences

        val titleText = DynamicTextReference("WorldKit - No Project", 67, TEXT_STYLE_NORMAL)

        val errorHandler: ErrorDialog = object : ErrorDialog {

            override fun displayErrorMessage(message: String?) {
                errorMessageReference.value = message ?: "An unknown error occurred."
                dialogLayer.isVisible = true
                errorMessageDialog.isVisible = true
                dialogCallback.value = {
                    dialogCallback.value = noop
                }
            }
        }

        val uiLayout = layout { ui ->
            val uiLayout = this
            ui {
                disableCursor.addListener { old, new ->
                    if (old != new) {
                        if (new) {
                            disableCursor()
                        } else {
                            enableCursor()
                        }
                    }
                }
                hideCursor.addListener { old, new ->
                    if (old != new) {
                        if (new) {
                            hideCursor()
                        } else {
                            showCursor()
                        }
                    }
                }
                background.set(45, 45, 48)

                textFont.value = createFont("/fonts/Roboto.ttf", "Roboto")
                glyphFont.value = createFont("/fonts/WorldKitUi.ttf", "Glyphs")

                val maxRestoreGlyph = MemoryUtil.memUTF8(if (isMaximized) GLYPH_RESTORE else GLYPH_MAXIMIZE, false)
                maximizeHandler = {
                    MemoryUtil.memUTF8(GLYPH_RESTORE, false, maxRestoreGlyph, 0)
                }
                restoreHandler = {
                    MemoryUtil.memUTF8(GLYPH_MAXIMIZE, false, maxRestoreGlyph, 0)
                }

                val (texId, texWidth, texHeight) = loadTexture2D(GL11.GL_LINEAR_MIPMAP_NEAREST, GL11.GL_LINEAR, "/textures/icon/512.png", true, true, 0, 0.0f,
                        "/textures/icon/256.png",
                        "/textures/icon/128.png",
                        "/textures/icon/64.png",
                        "/textures/icon/32.png",
                        "/textures/icon/16.png")
                icon.value = createImage(texId, texWidth, texHeight, 0)

                setWindowIcon(createGlfwImages(
                        "/textures/icon/16.png",
                        "/textures/icon/20.png",
                        "/textures/icon/24.png",
                        "/textures/icon/30.png",
                        "/textures/icon/32.png",
                        "/textures/icon/36.png",
                        "/textures/icon/40.png",
                        "/textures/icon/48.png",
                        "/textures/icon/50.png",
                        "/textures/icon/60.png",
                        "/textures/icon/64.png",
                        "/textures/icon/72.png",
                        "/textures/icon/80.png",
                        "/textures/icon/96.png",
                        "/textures/icon/128.png",
                        "/textures/icon/160.png",
                        "/textures/icon/192.png",
                        "/textures/icon/256.png",
                        "/textures/icon/320.png",
                        "/textures/icon/384.png",
                        "/textures/icon/512.png"
                ))

                meshViewport.init()

                root {
                    mainLayer = block {
                        isFallThrough = true
                    }
                    menuLayer = block {
                        isFallThrough = true
                    }
                    panelLayer = block {
                        isFallThrough = false
                        isMouseAware = true
                        isVisible = false
                        shape = FILL_GREY_OUT
                    }
                    dropdownLayer = block {
                        isFallThrough = true
                    }
                    dialogLayer = block {
                        isFallThrough = false
                        isMouseAware = true
                        isVisible = false
                        shape = FILL_GREY_OUT
                    }
                    dialogDropdownLayer = block {
                        isFallThrough = true
                    }
                    mouseOverlayLayer = block {
                        isFallThrough = true
                    }
                }
                rootRef.value = root
                dialogLayer {
                    overwriteWarningDialog = dialog(400.0f, 160.0f, overwriteWarningText, BLOCK_GLYPH_WARNING(60.0f)) {
                        button(text("Yes"), DIALOG_BUTTON_STYLE) {
                            overwriteWarningDialog.isVisible = false
                            val saved = saveProject(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler)
                            dialogLayer.isVisible = false
                            if (saved) {
                                dialogCallback.value()
                            }
                        }.with { width = 60.0f }
                        hSpacer(SMALL_SPACER_SIZE)
                        button(text("No"), DIALOG_BUTTON_STYLE) {
                            overwriteWarningDialog.isVisible = false
                            dialogLayer.isVisible = false
                            dialogCallback.value()
                        }.with { width = 60.0f }
                        hSpacer(SMALL_SPACER_SIZE)
                        button(text("Cancel"), DIALOG_BUTTON_STYLE) {
                            overwriteWarningDialog.isVisible = false
                            dialogLayer.isVisible = false
                        }.with { width = 60.0f }
                    }
                    errorMessageDialog = dialog(500.0f, 190.0f, errorMessageText, BLOCK_GLYPH_ERROR(60.0f)) {
                        button(text("OK"), DIALOG_BUTTON_STYLE) {
                            errorMessageDialog.isVisible = false
                            dialogLayer.isVisible = false
                            dialogCallback.value()
                        }.with { width = 60.0f }
                    }
                    generatingMessageBlock = block {
                        hAlign = CENTER
                        vAlign = MIDDLE
                        hSizing = SHRINK
                        vSizing = SHRINK
                        block {
                            layout = VERTICAL
                            hAlign = CENTER
                            vAlign = TOP
                            hSizing = SHRINK
                            vSizing = SHRINK
                            text = generatingPrimaryMessage
                            isVisible = true
                        }
                        vSpacer(MEDIUM_SPACER_SIZE)
                        block {
                            layout = VERTICAL
                            hAlign = CENTER
                            vAlign = TOP
                            hSizing = SHRINK
                            vSizing = SHRINK
                            text = generatingSecondaryMessage
                            isVisible = true
                        }
                        isVisible = false
                    }
                }
                if (preferences.isFirstRun) {
                    generatingPrimaryMessage.reference.value = text("Performing one-time setup tasks... 0:00", TEXT_STYLE_LARGE_MESSAGE)
                    generatingSecondaryMessage.reference.value = text("This may take a few minutes.", TEXT_STYLE_SMALL_MESSAGE)
                } else {
                    generatingPrimaryMessage.reference.value = text("Loading... 0:00", TEXT_STYLE_LARGE_MESSAGE)
                    generatingSecondaryMessage.reference.value = text("  ", TEXT_STYLE_SMALL_MESSAGE)
                }
                generatingMessageBlock.isVisible = true
                dialogLayer.isVisible = true
                currentProject.addListener { _, new ->
                    doOnMainThread {
                        updateTitle(titleText, new)
                        doesActiveProjectExist.value = new != null
                        if (new != null) {
                            addProjectToRecentProjects(new.file, dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
                        }
                    }
                }
                currentProjectHasModifications.addListener { _, new ->
                    doOnMainThread {
                        updateTitle(titleText, currentProject.value, new)
                    }
                }
                preferencesPanel(ui)
                exportPanel(ui)
                tilePanel(ui)
                aboutPanel(ui)
                customBiomePanel(ui)
                mainLayer {
                    block {
                        vSizing = STATIC
                        height = MEDIUM_ROW_HEIGHT
                        layout = VERTICAL
                        icon(icon.value, SMALL_ROW_HEIGHT, MEDIUM_ROW_HEIGHT)
                        hSpacer(SMALL_SPACER_SIZE)
                        menuBar(menuLayer, MEDIUM_ROW_HEIGHT, TEXT_STYLE_BUTTON, COLOR_DISABLED_CLICKABLE) {
                            menu("File") {
                                menuItem("New project", "Ctrl+N", BLOCK_GLYPH_NEW_FILE) {
                                    newProject(overwriteWarningReference, overwriteWarningDialog, dialogCallback, noop)
                                }
                                menuItem("Open project...", "Ctrl+O", BLOCK_GLYPH_OPEN_FOLDER) {
                                    openProject(ui, errorHandler)
                                }
                                menuItem("Save", "Ctrl+S", BLOCK_GLYPH_SAVE, isActive = currentProjectHasModifications) {
                                    saveProject(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler)
                                }
                                menuItem("Save as...", "Shift+Ctrl+S", BLOCK_GLYPH_SAVE, isActive = doesActiveProjectExist) {
                                    saveProjectAs(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler)
                                }
                                menuItem("Close", isActive = doesActiveProjectExist) {
                                    if (currentProject.value != null && currentProjectHasModifications.value) {
                                        dialogLayer.isVisible = true
                                        overwriteWarningReference.value = "Do you want to save the current project before closing?"
                                        overwriteWarningDialog.isVisible = true
                                        dialogCallback.value = {
                                            meshViewport.reset()
                                            imageMode.value = 3
                                            currentProject.value = null
                                            dialogCallback.value = noop
                                        }
                                    }
                                }
                                menuDivider()
                                subMenu("Open recent", isActive = recentProjectsAvailable) {
                                    recentProjectsDropdown.value = this
                                    recentAutosavesDivider.value = menuDivider()
                                    recentAutosavesDivider.value?.isVisible = false
                                    menuDivider()
                                    menuItem("Clear recent file list", isActive = recentProjectsAvailable) {
                                        clearRecentProjects()
                                    }
                                }
                                menuDivider()
                                menuItem("Export maps...", "Ctrl+E", isActive = doesActiveProjectExist) {
                                    exportMaps()
                                }
                                menuItem("Tile maps...", isActive = ref(true)) {
                                    tileMaps()
                                }
                                menuDivider()
                                menuItem("Exit", "Ctrl+Q", BLOCK_GLYPH_CLOSE) {
                                    closeWindowSafely()
                                }
                            }
                            menu("Settings") {
                                menuItem("Preferences", "Ctrl+P", BLOCK_GLYPH_GEAR) {
                                    openPreferences()
                                }
                                menuItem("Restore default preferences") {
                                    val defaults = Preferences()
                                    defaults.isFirstRun = false
                                    preferences.rememberWindowState = defaults.rememberWindowState
                                    preferences.projectDir = defaults.projectDir
                                    preferences.tempDir = defaults.tempDir
                                    rememberWindowState.value = preferences.rememberWindowState
                                    projectDir.reference.value = preferences.projectDir.canonicalPath
                                    tempDir.reference.value = preferences.tempDir.canonicalPath
                                    savePreferences(preferences)
                                }
                            }
                            menu("Help") {
                                menuItem("Website") {
                                    openWebPage("https://github.com/adambrown/WorldKit", errorHandler)
                                }
                                menuItem("About WorldKit") {
                                    openAboutPanel()
                                }
                            }
                        }
                        hSpacer(SMALL_SPACER_SIZE)
                        dragArea = dragArea(titleText.text)
                        hSpacer(SMALL_SPACER_SIZE)
                        button(glyph(GLYPH_MINIMIZE), WINDOW_DECORATE_BUTTON_STYLE) { minimizeWindow() }
                        button(glyph(maxRestoreGlyph), WINDOW_DECORATE_BUTTON_STYLE) { toggleMaximized() }
                        button(glyph(GLYPH_CLOSE), WINDOW_DECORATE_BUTTON_STYLE) {
                            closeWindowSafely()
                        }
                    }
                    loadRecentProjects(dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
                    loadRecentAutosaves(dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
                    block {
                        vSizing = GROW
                        layout = VERTICAL
                        hAlign = LEFT
                        leftPanel(ui, uiLayout, dialogLayer)
                        block {
                            hSizing = GROW
                            layout = HORIZONTAL
                            hAlign = LEFT
                            block {
                                yOffset = SMALL_SPACER_SIZE
                                width = -SMALL_SPACER_SIZE
                                height = -2 * SMALL_SPACER_SIZE
                                block {
                                    xOffset = 1.0f
                                    yOffset = 1.0f
                                    width = -2.0f
                                    height = -2.0f
                                    meshViewport3D(meshViewport, ui)
                                    block {
                                        val toolbar = this
                                        hSizing = STATIC
                                        width = 500.0f
                                        var tools = NO_BLOCK
                                        var expandToolbarButton = NO_BLOCK
                                        var collapseToolbarButton = NO_BLOCK
                                        isFallThrough = true
                                        vButtonRow(LARGE_ROW_HEIGHT, LEFT, TOP) {
                                            expandToolbarButton = button(text("+"), LARGE_TEXT_BUTTON_STYLE) {
                                                tools.isVisible = true
                                                tools.isMouseAware = true
                                                collapseToolbarButton.isVisible = true
                                                collapseToolbarButton.isMouseAware = true
                                                expandToolbarButton.isVisible = false
                                                expandToolbarButton.isMouseAware = false
                                                toolbar.shape = BACKGROUND_RECT
                                            }
                                            collapseToolbarButton = button(text("-"), LARGE_TEXT_BUTTON_STYLE) {
                                                tools.isVisible = false
                                                tools.isMouseAware = false
                                                collapseToolbarButton.isVisible = false
                                                collapseToolbarButton.isMouseAware = false
                                                expandToolbarButton.isVisible = true
                                                expandToolbarButton.isMouseAware = true
                                                toolbar.shape = NO_SHAPE
                                            }
                                            collapseToolbarButton.isVisible = false
                                        }
                                        tools = block {
                                            isVisible = false
                                            vSizing = SHRINK
                                            xOffset = SMALL_SPACER_SIZE
                                            yOffset = SMALL_SPACER_SIZE
                                            width = -2 * SMALL_SPACER_SIZE
                                            layout = VERTICAL
                                            val shrinkGroup = hShrinkGroup()
                                            vToggleRow(waterPlaneOn, LARGE_ROW_HEIGHT, text("Water:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                                            vToggleRow(heightColorsOn, LARGE_ROW_HEIGHT, text("Colors:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                                            vToggleRow(riversOn, LARGE_ROW_HEIGHT, text("Rivers:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                                            vToggleRow(skyOn, LARGE_ROW_HEIGHT, text("Sky:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                                            vToggleRow(fogOn, LARGE_ROW_HEIGHT, text("Fog:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                                            vToggleRow(perspectiveOn, LARGE_ROW_HEIGHT, text("Perspective:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                                            vSliderRow(heightMapScaleFactor, LARGE_ROW_HEIGHT, text("Height scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, heightScaleFunction, heightScaleFunctionInverse)
                                            vSliderRow(waterShaderParams.level, LARGE_ROW_HEIGHT, text("Water level:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.003f, 0.175f), linearClampedScaleFunctionInverse(0.003f, 0.175f))
                                            vButtonRow(LARGE_ROW_HEIGHT) {
                                                button(text("Reset view"), NORMAL_TEXT_BUTTON_STYLE) { resetView.value = true }
                                                button(text("Reset height"), NORMAL_TEXT_BUTTON_STYLE) { heightMapScaleFactor.value = defaultHeightScale }
                                            }
                                            vExpandPanel("Light") {
                                                vSliderWithValueRow(indirectIntensity, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Indirect light:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 2.0f), linearClampedScaleFunctionInverse(0.0f, 2.0f))
                                                vExpandPanel("Color") {
                                                    vSliderWithValueRow(lightColor[0], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Red:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 10.0f), linearClampedScaleFunctionInverse(0.0f, 10.0f))
                                                    vSliderWithValueRow(lightColor[1], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Green:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 10.0f), linearClampedScaleFunctionInverse(0.0f, 10.0f))
                                                    vSliderWithValueRow(lightColor[2], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Blue:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 10.0f), linearClampedScaleFunctionInverse(0.0f, 10.0f))
                                                }
                                                vExpandPanel("Direction") {
                                                    vSliderWithValueRow(lightElevation, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Elevation:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.1f, 60.0f), linearClampedScaleFunctionInverse(0.1f, 60.0f))
                                                    vSliderWithValueRow(lightHeading, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Heading:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(-180.0f, 180.0f), linearClampedScaleFunctionInverse(-180.0f, 180.0f))
                                                }
                                            }
                                            vExpandPanel("Land material") {
                                                vExpandPanel("Color") {
                                                    vSliderWithValueRow(baseColor[0], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Red:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                    vSliderWithValueRow(baseColor[1], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Green:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                    vSliderWithValueRow(baseColor[2], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Blue:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                }
                                                vSliderWithValueRow(metallic, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Metallic:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                vSliderWithValueRow(roughness, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Roughness:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                vSliderWithValueRow(specularIntensity, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Specular:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                            }
                                            vExpandPanel("Water material") {
                                                vExpandPanel("Color") {
                                                    vSliderWithValueRow(waterShaderParams.color[0], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Red:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                    vSliderWithValueRow(waterShaderParams.color[1], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Green:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                    vSliderWithValueRow(waterShaderParams.color[2], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Blue:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                }
                                                vSliderWithValueRow(waterShaderParams.metallic, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Metallic:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                vSliderWithValueRow(waterShaderParams.roughness, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Roughness:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                vSliderWithValueRow(waterShaderParams.specularIntensity, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Specular:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                vExpandPanel("Normal offsets") {
                                                    val highPrecisionToString = { f: Float ->
                                                        String.format("%.6f", f)
                                                    }
                                                    vSliderWithValueRow(waterShaderParams.normalOffsets[0], 8, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("0:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 0.5f), linearClampedScaleFunctionInverse(0.0f, 0.5f), highPrecisionToString)
                                                    vSliderWithValueRow(waterShaderParams.normalOffsets[1], 8, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("1:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 0.2f), linearClampedScaleFunctionInverse(0.0f, 0.2f), highPrecisionToString)
                                                    vSliderWithValueRow(waterShaderParams.normalOffsets[2], 8, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("2:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 0.1f), linearClampedScaleFunctionInverse(0.0f, 0.1f), highPrecisionToString)
                                                    vSliderWithValueRow(waterShaderParams.normalOffsets[3], 8, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("3:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 0.01f), linearClampedScaleFunctionInverse(0.0f, 0.01f), highPrecisionToString)
                                                    vSliderWithValueRow(waterShaderParams.normalOffsets[4], 8, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("4:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 0.005f), linearClampedScaleFunctionInverse(0.0f, 0.005f), highPrecisionToString)
                                                }
                                                vExpandPanel("Normal strengths") {
                                                    vSliderWithValueRow(waterShaderParams.normalStrengths[0], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("0:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 3.0f), linearClampedScaleFunctionInverse(0.0f, 3.0f))
                                                    vSliderWithValueRow(waterShaderParams.normalStrengths[1], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("1:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 3.0f), linearClampedScaleFunctionInverse(0.0f, 3.0f))
                                                    vSliderWithValueRow(waterShaderParams.normalStrengths[2], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("2:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 3.0f), linearClampedScaleFunctionInverse(0.0f, 3.0f))
                                                    vSliderWithValueRow(waterShaderParams.normalStrengths[3], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("3:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 10.0f), linearClampedScaleFunctionInverse(0.0f, 20.0f))
                                                }
                                                vExpandPanel("Fades") {
                                                    vSliderWithValueRow(waterShaderParams.fadeStarts[0], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Start 0:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 50.0f), linearClampedScaleFunctionInverse(0.0f, 50.0f))
                                                    vSliderWithValueRow(waterShaderParams.fadeEnds[0], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("End 0:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 150.0f), linearClampedScaleFunctionInverse(0.0f, 150.0f))
                                                    vSliderWithValueRow(waterShaderParams.fadeStarts[1], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Start 1:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 3000.0f), linearClampedScaleFunctionInverse(0.0f, 3000.0f))
                                                    vSliderWithValueRow(waterShaderParams.fadeEnds[1], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("End 1:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 3000.0f), linearClampedScaleFunctionInverse(0.0f, 3000.0f))
                                                    vSliderWithValueRow(waterShaderParams.fadeStarts[2], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Start 2:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 3000.0f), linearClampedScaleFunctionInverse(0.0f, 3000.0f))
                                                    vSliderWithValueRow(waterShaderParams.fadeEnds[2], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("End 2:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 3000.0f), linearClampedScaleFunctionInverse(0.0f, 3000.0f))
                                                }
                                            }
                                            vExpandPanel("Fog") {
                                                vExpandPanel("Color") {
                                                    vSliderWithValueRow(fogShaderParams.color[0], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Red:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                    vSliderWithValueRow(fogShaderParams.color[1], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Green:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                    vSliderWithValueRow(fogShaderParams.color[2], 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Blue:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                }
                                                vSliderWithValueRow(fogShaderParams.atmosphericFogDensity, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Atmosphere density:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 0.2f), linearClampedScaleFunctionInverse(0.0f, 0.2f))
                                                vSliderWithValueRow(fogShaderParams.exponentialFogDensity, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Fog density:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 0.2f), linearClampedScaleFunctionInverse(0.0f, 0.2f))
                                                vSliderWithValueRow(fogShaderParams.exponentialFogHeightFalloff, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Fog falloff:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 1.0f), linearClampedScaleFunctionInverse(0.0f, 1.0f))
                                                vSliderWithValueRow(fogShaderParams.fogHeightClampPower, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Fog clamp:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(-100.0f, 100.0f), linearClampedScaleFunctionInverse(-100.0f, 100.0f))
                                            }
                                        }
                                    }
                                }
                                block {
                                    shape = SHAPE_BORDER_ONLY
                                    canOverflow = true
                                    isMouseAware = false
                                }
                            }
                        }
                    }
                    resizeAreaSouthEast = resizeArea(ShapeTriangle.Direction.SOUTH_EAST)
                    resizeAreaSouthWest = resizeArea(ShapeTriangle.Direction.SOUTH_WEST)
                }
                mouseOverlayLayer {
                    brushShapeOuter = block {
                        layout = ABSOLUTE
                        hSizing = STATIC
                        vSizing = STATIC
                        width = 10.0f
                        height = 10.0f
                        shape = ShapeCircle(NO_FILL, STROKE_WHITE_1)
                        isFallThrough = true
                        isMouseAware = false
                    }
                    brushShapeInner = block {
                        layout = ABSOLUTE
                        hSizing = STATIC
                        vSizing = STATIC
                        width = 8.0f
                        height = 8.0f
                        shape = ShapeCircle(NO_FILL, STROKE_BLACK_1)
                        isFallThrough = true
                        isMouseAware = false
                    }
                }
                brushActive.addListener { _, new ->
                    mouseOverlayLayer.isVisible = new
                    mouseOverlayLayer.isMouseAware = new
                    brushShapeOuter.isVisible = new
                    brushShapeOuter.isMouseAware = new
                    brushShapeInner.isVisible = new
                    brushShapeInner.isMouseAware = new
                    if (new) {
                        mouseOverlayLayer.onTick = { x, y ->
                            if (brushActive.value) {
                                val brushSize = brushSize.value
                                brushShapeOuter.xOffset = x - (brushSize / 2.0f)
                                brushShapeOuter.yOffset = y - (brushSize / 2.0f)
                                brushShapeOuter.width = brushSize
                                brushShapeOuter.height = brushSize
                                brushShapeInner.xOffset = x - ((brushSize - 2) / 2.0f)
                                brushShapeInner.yOffset = y - ((brushSize - 2) / 2.0f)
                                brushShapeInner.width = brushSize - 2
                                brushShapeInner.height = brushSize - 2
                            }
                        }
                    } else {
                        mouseOverlayLayer.onTick = null
                    }
                }
                brushActive.value = false
                if (!EXPERIMENTAL_BUILD) {
                    experimentalWidgets.forEach {
                        it.isVisible = false
                    }
                }
                hotKeyHandler = HotKeyHandler { key, _, action, mods ->
                    if (action == GLFW.GLFW_PRESS) {
                        val ctrl = GLFW.GLFW_MOD_CONTROL and mods != 0
                        val shift = GLFW.GLFW_MOD_SHIFT and mods != 0
                        if (ctrl) {
                            when (key) {
                                GLFW.GLFW_KEY_N -> {
                                    task { newProject(overwriteWarningReference, overwriteWarningDialog, dialogCallback, noop) }
                                    true
                                }
                                GLFW.GLFW_KEY_O -> {
                                    task { openProject(ui, errorHandler) }
                                    true
                                }
                                GLFW.GLFW_KEY_S -> {
                                    if (shift) {
                                        task { saveProjectAs(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler) }
                                    } else {
                                        task { saveProject(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler) }
                                    }
                                    true
                                }
                                GLFW.GLFW_KEY_E -> {
                                    task { exportMaps() }
                                    true
                                }
                                GLFW.GLFW_KEY_Q -> {
                                    task { closeWindowSafely() }
                                    true
                                }
                                GLFW.GLFW_KEY_P -> {
                                    task { openPreferences() }
                                    true
                                }
                                GLFW.GLFW_KEY_F1 -> {
                                    task { openHelp(errorHandler) }
                                    false
                                }
                                else -> {
                                    false
                                }
                            }
                        } else {
                            if (key == GLFW.GLFW_KEY_ESCAPE) {
                                if (cancelCurrentRunningTask.value?.value == false) {
                                    cancelCurrentRunningTask.value?.value = true
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        }
                    } else {
                        false
                    }
                }
                val autosaveTimer = Timer(true)
                autosaveTimer.schedule(object: TimerTask() {
                    override fun run() {
                        if (currentProjectHasModifications.value) {
                            task {
                                autosave(currentProject.value, dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
                                ui.saveWindowState()
                            }
                        }
                    }
                }, 30000, 30000)
            }
        }
        var wasResizing = false
        var wasMinimized = true
        var wasJustMaximized = false

//        for (i in 0 until indexColors.size step 4) {
//            (0..3).map { indexColors[i + it] }.forEach { it -> print("color(${it.rInt}, ${it.gInt}, ${it.bInt}), ") }
//            println()
//        }
        val loadTimer = Timer(true)
        ui(uiLayout, preferences.windowState, afterShow = {
            val ui = this
            val message = if (preferences.isFirstRun) {
                "Performing one-time setup tasks..."
            } else {
                "Loading..."
            }
            val startTime = System.currentTimeMillis()
            loadTimer.schedule(object: TimerTask() {
                override fun run() {
                    val currentTime = System.currentTimeMillis()
                    val elapsedTime = (currentTime - startTime)
                    val seconds = String.format("%02d", (elapsedTime / 1000).toInt() % 60)
                    val minutes = (elapsedTime / (1000 * 60) % 60).toInt()
                    generatingPrimaryMessage.reference.value = text("$message $minutes:$seconds", TEXT_STYLE_LARGE_MESSAGE)
                    ui.layout.root.movedOrResized = true
                }
            }, 1000, 1000)
            uiIsShown = true
        }, afterLoad = {
            doOnMainThread {
                if (args.isNotEmpty()) {
                    val projectFile = File(args[0])
                    openProject(errorHandler, projectFile)
                }
                if (preferences.isFirstRun) {
                    preferences.isFirstRun = false
                    savePreferences(preferences)
                }
                BIOME_TEMPLATES_REF.value?.mapBiomeNames(BIOME_NAMES_AS_TEXT)
                loadTimer.cancel()
                generatingMessageBlock.isVisible = false
                dialogLayer.isVisible = false
            }
        }) {
            wasResizing = if (isResizing) {
                true
            } else {
                if (wasResizing) {
                    onWindowResize()
                }
                false
            }
            if (wasJustMaximized) {
                onWindowResize()
                wasJustMaximized = false
            }
            if (isMaximized) {
                if (wasMinimized) {
                    wasJustMaximized = true
                }
                wasMinimized = false
            } else {
                wasMinimized = true
            }
            performMainThreadTasks()
        }
    }
}

//private val indexColors = arrayListOf(
//    "#000000", "#FFFF00", "#1CE6FF", "#FF34FF", "#FF4A46", "#008941", "#006FA6", "#A30059",
//    "#FFDBE5", "#7A4900", "#0000A6", "#63FFAC", "#B79762", "#004D43", "#8FB0FF", "#997D87",
//    "#5A0007", "#809693", "#FEFFE6", "#1B4400", "#4FC601", "#3B5DFF", "#4A3B53", "#FF2F80",
//    "#61615A", "#BA0900", "#6B7900", "#00C2A0", "#FFAA92", "#FF90C9", "#B903AA", "#D16100",
//    "#DDEFFF", "#000035", "#7B4F4B", "#A1C299", "#300018", "#0AA6D8", "#013349", "#00846F",
//    "#372101", "#FFB500", "#C2FFED", "#A079BF", "#CC0744", "#C0B9B2", "#C2FF99", "#001E09",
//    "#00489C", "#6F0062", "#0CBD66", "#EEC3FF", "#456D75", "#B77B68", "#7A87A1", "#788D66",
//    "#885578", "#FAD09F", "#FF8A9A", "#D157A0", "#BEC459", "#456648", "#0086ED", "#886F4C",
//    "#34362D", "#B4A8BD", "#00A6AA", "#452C2C", "#636375", "#A3C8C9", "#FF913F", "#938A81",
//    "#575329", "#00FECF", "#B05B6F", "#8CD0FF", "#3B9700", "#04F757", "#C8A1A1", "#1E6E00",
//    "#7900D7", "#A77500", "#6367A9", "#A05837", "#6B002C", "#772600", "#D790FF", "#9B9700",
//    "#549E79", "#FFF69F", "#201625", "#72418F", "#BC23FF", "#99ADC0", "#3A2465", "#922329",
//    "#5B4534", "#FDE8DC", "#404E55", "#0089A3", "#CB7E98", "#A4E804", "#324E72", "#6A3A4C",
//    "#83AB58", "#001C1E", "#D1F7CE", "#004B28", "#C8D0F6", "#A3A489", "#806C66", "#222800",
//    "#BF5650", "#E83000", "#66796D", "#DA007C", "#FF1A59", "#8ADBB4", "#1E0200", "#5B4E51",
//    "#C895C5", "#320033", "#FF6832", "#66E1D3", "#CFCDAC", "#D0AC94", "#7ED379", "#012C58",
//    "#7A7BFF", "#D68E01", "#353339", "#78AFA1", "#FEB2C6", "#75797C", "#837393", "#943A4D",
//    "#B5F4FF", "#D2DCD5", "#9556BD", "#6A714A", "#001325", "#02525F", "#0AA3F7", "#E98176",
//    "#DBD5DD", "#5EBCD1", "#3D4F44", "#7E6405", "#02684E", "#962B75", "#8D8546", "#9695C5",
//    "#E773CE", "#D86A78", "#3E89BE", "#CA834E", "#518A87", "#5B113C", "#55813B", "#E704C4",
//    "#00005F", "#A97399", "#4B8160", "#59738A", "#FF5DA7", "#F7C9BF", "#643127", "#513A01",
//    "#6B94AA", "#51A058", "#A45B02", "#1D1702", "#E20027", "#E7AB63", "#4C6001", "#9C6966",
//    "#64547B", "#97979E", "#006A66", "#391406", "#F4D749", "#0045D2", "#006C31", "#DDB6D0",
//    "#7C6571", "#9FB2A4", "#00D891", "#15A08A", "#BC65E9", "#FFFFFE", "#C6DC99", "#203B3C",
//    "#671190", "#6B3A64", "#F5E1FF", "#FFA0F2", "#CCAA35", "#374527", "#8BB400", "#797868",
//    "#C6005A", "#3B000A", "#C86240", "#29607C", "#402334", "#7D5A44", "#CCB87C", "#B88183",
//    "#AA5199", "#B5D6C3", "#A38469", "#9F94F0", "#A74571", "#B894A6", "#71BB8C", "#00B433",
//    "#789EC9", "#6D80BA", "#953F00", "#5EFF03", "#E4FFFC", "#1BE177", "#BCB1E5", "#76912F",
//    "#003109", "#0060CD", "#D20096", "#895563", "#29201D", "#5B3213", "#A76F42", "#89412E",
//    "#1A3A2A", "#494B5A", "#A88C85", "#F4ABAA", "#A3F3AB", "#00C6C8", "#EA8B66", "#958A9F",
//    "#BDC9D2", "#9FA064", "#BE4700", "#658188", "#83A485", "#453C23", "#47675D", "#3A3F00",
//    "#061203", "#DFFB71", "#868E7E", "#98D058", "#6C8F7D", "#D7BFC2", "#3C3E6E", "#D83D66",
//    "#2F5D9B", "#6C5E46", "#D25B88", "#5B656C", "#00B57F", "#545C46", "#866097", "#365D25",
//    "#252F99", "#00CCFF", "#674E60", "#FC009C", "#92896B", "#1E2324", "#DEC9B2", "#9D4948",
//    "#85ABB4", "#342142", "#D09685", "#A4ACAC", "#00FFFF", "#AE9C86", "#742A33", "#0E72C5",
//    "#AFD8EC", "#C064B9", "#91028C", "#FEEDBF", "#FFB789", "#9CB8E4", "#AFFFD1", "#2A364C",
//    "#4F4A43", "#647095", "#34BBFF", "#807781", "#920003", "#B3A5A7", "#018615", "#F1FFC8",
//    "#976F5C", "#FF3BC1", "#FF5F6B", "#077D84", "#F56D93", "#5771DA", "#4E1E2A", "#830055",
//    "#02D346", "#BE452D", "#00905E", "#BE0028", "#6E96E3", "#007699", "#FEC96D", "#9C6A7D",
//    "#3FA1B8", "#893DE3", "#79B4D6", "#7FD4D9", "#6751BB", "#B28D2D", "#E27A05", "#DD9CB8",
//    "#AABC7A", "#980034", "#561A02", "#8F7F00", "#635000", "#CD7DAE", "#8A5E2D", "#FFB3E1",
//    "#6B6466", "#C6D300", "#0100E2", "#88EC69", "#8FCCBE", "#21001C", "#511F4D", "#E3F6E3",
//    "#FF8EB1", "#6B4F29", "#A37F46", "#6A5950", "#1F2A1A", "#04784D", "#101835", "#E6E0D0",
//    "#FF74FE", "#00A45F", "#8F5DF8", "#4B0059", "#412F23", "#D8939E", "#DB9D72", "#604143",
//    "#B5BACE", "#989EB7", "#D2C4DB", "#A587AF", "#77D796", "#7F8C94", "#FF9B03", "#555196",
//    "#31DDAE", "#74B671", "#802647", "#2A373F", "#014A68", "#696628", "#4C7B6D", "#002C27",
//    "#7A4522", "#3B5859", "#E5D381", "#FFF3FF", "#679FA0", "#261300", "#2C5742", "#9131AF",
//    "#AF5D88", "#C7706A", "#61AB1F", "#8CF2D4", "#C5D9B8", "#9FFFFB", "#BF45CC", "#493941",
//    "#863B60", "#B90076", "#003177", "#C582D2", "#C1B394", "#602B70", "#887868", "#BABFB0",
//    "#030012", "#D1ACFE", "#7FDEFE", "#4B5C71", "#A3A097", "#E66D53", "#637B5D", "#92BEA5",
//    "#00F8B3", "#BEDDFF", "#3DB5A7", "#DD3248", "#B6E4DE", "#427745", "#598C5A", "#B94C59",
//    "#8181D5", "#94888B", "#FED6BD", "#536D31", "#6EFF92", "#E4E8FF", "#20E200", "#FFD0F2",
//    "#4C83A1", "#BD7322", "#915C4E", "#8C4787", "#025117", "#A2AA45", "#2D1B21", "#A9DDB0",
//    "#FF4F78", "#528500", "#009A2E", "#17FCE4", "#71555A", "#525D82", "#00195A", "#967874",
//    "#555558", "#0B212C", "#1E202B", "#EFBFC4", "#6F9755", "#6F7586", "#501D1D", "#372D00",
//    "#741D16", "#5EB393", "#B5B400", "#DD4A38", "#363DFF", "#AD6552", "#6635AF", "#836BBA",
//    "#98AA7F", "#464836", "#322C3E", "#7CB9BA", "#5B6965", "#707D3D", "#7A001D", "#6E4636",
//    "#443A38", "#AE81FF", "#489079", "#897334", "#009087", "#DA713C", "#361618", "#FF6F01",
//    "#006679", "#370E77", "#4B3A83", "#C9E2E6", "#C44170", "#FF4526", "#73BE54", "#C4DF72",
//    "#ADFF60", "#00447D", "#DCCEC9", "#BD9479", "#656E5B", "#EC5200", "#FF6EC2", "#7A617E",
//    "#DDAEA2", "#77837F", "#A53327", "#608EFF", "#B599D7", "#A50149", "#4E0025", "#C9B1A9",
//    "#03919A", "#1B2A25", "#E500F1", "#982E0B", "#B67180", "#E05859", "#006039", "#578F9B",
//    "#305230", "#CE934C", "#B3C2BE", "#C0BAC0", "#B506D3", "#170C10", "#4C534F", "#224451",
//    "#3E4141", "#78726D", "#B6602B", "#200441", "#DDB588", "#497200", "#C5AAB6", "#033C61",
//    "#71B2F5", "#A9E088", "#4979B0", "#A2C3DF", "#784149", "#2D2B17", "#3E0E2F", "#57344C",
//    "#0091BE", "#E451D1", "#4B4B6A", "#5C011A", "#7C8060", "#FF9491", "#4C325D", "#005C8B",
//    "#E5FDA4", "#68D1B6", "#032641", "#140023", "#8683A9", "#CFFF00", "#A72C3E", "#34475A",
//    "#B1BB9A", "#B4A04F", "#8D918E", "#A168A6", "#813D3A", "#425218", "#DA8386", "#776133",
//    "#563930", "#8498AE", "#90C1D3", "#B5666B", "#9B585E", "#856465", "#AD7C90", "#E2BC00",
//    "#E3AAE0", "#B2C2FE", "#FD0039", "#009B75", "#FFF46D", "#E87EAC", "#DFE3E6", "#848590",
//    "#AA9297", "#83A193", "#577977", "#3E7158", "#C64289", "#EA0072", "#C4A8CB", "#55C899",
//    "#E78FCF", "#004547", "#F6E2E3", "#966716", "#378FDB", "#435E6A", "#DA0004", "#1B000F",
//    "#5B9C8F", "#6E2B52", "#011115", "#E3E8C4", "#AE3B85", "#EA1CA9", "#FF9E6B", "#457D8B",
//    "#92678B", "#00CDBB", "#9CCC04", "#002E38", "#96C57F", "#CFF6B4", "#492818", "#766E52",
//    "#20370E", "#E3D19F", "#2E3C30", "#B2EACE", "#F3BDA4", "#A24E3D", "#976FD9", "#8C9FA8",
//    "#7C2B73", "#4E5F37", "#5D5462", "#90956F", "#6AA776", "#DBCBF6", "#DA71FF", "#987C95",
//    "#52323C", "#BB3C42", "#584D39", "#4FC15F", "#A2B9C1", "#79DB21", "#1D5958", "#BD744E",
//    "#160B00", "#20221A", "#6B8295", "#00E0E4", "#102401", "#1B782A", "#DAA9B5", "#B0415D",
//    "#859253", "#97A094", "#06E3C4", "#47688C", "#7C6755", "#075C00", "#7560D5", "#7D9F00",
//    "#C36D96", "#4D913E", "#5F4276", "#FCE4C8", "#303052", "#4F381B", "#E5A532", "#706690",
//    "#AA9A92", "#237363", "#73013E", "#FF9079", "#A79A74", "#029BDB", "#FF0169", "#C7D2E7",
//    "#CA8869", "#80FFCD", "#BB1F69", "#90B0AB", "#7D74A9", "#FCC7DB", "#99375B", "#00AB4D",
//    "#ABAED1", "#BE9D91", "#E6E5A7", "#332C22", "#DD587B", "#F5FFF7", "#5D3033", "#6D3800",
//    "#FF0020", "#B57BB3", "#D7FFE6", "#C535A9", "#260009", "#6A8781", "#A8ABB4", "#D45262",
//    "#794B61", "#4621B2", "#8DA4DB", "#C7C890", "#6FE9AD", "#A243A7", "#B2B081", "#181B00",
//    "#286154", "#4CA43B", "#6A9573", "#A8441D", "#5C727B", "#738671", "#D0CFCB", "#897B77",
//    "#1F3F22", "#4145A7", "#DA9894", "#A1757A", "#63243C", "#ADAAFF", "#00CDE2", "#DDBC62",
//    "#698EB1", "#208462", "#00B7E0", "#614A44", "#9BBB57", "#7A5C54", "#857A50", "#766B7E",
//    "#014833", "#FF8347", "#7A8EBA", "#274740", "#946444", "#EBD8E6", "#646241", "#373917",
//    "#6AD450", "#81817B", "#D499E3", "#979440", "#011A12", "#526554", "#B5885C", "#A499A5",
//    "#03AD89", "#B3008B", "#E3C4B5", "#96531F", "#867175", "#74569E", "#617D9F", "#E70452",
//    "#067EAF", "#A697B6", "#B787A8", "#9CFF93", "#311D19", "#3A9459", "#6E746E", "#B0C5AE",
//    "#84EDF7", "#ED3488", "#754C78", "#384644", "#C7847B", "#00B6C5", "#7FA670", "#C1AF9E",
//    "#2A7FFF", "#72A58C", "#FFC07F", "#9DEBDD", "#D97C8E", "#7E7C93", "#62E674", "#B5639E",
//    "#FFA861", "#C2A580", "#8D9C83", "#B70546", "#372B2E", "#0098FF", "#985975", "#20204C",
//    "#FF6C60", "#445083", "#8502AA", "#72361F", "#9676A3", "#484449", "#CED6C2", "#3B164A",
//    "#CCA763", "#2C7F77", "#02227B", "#A37E6F", "#CDE6DC", "#CDFFFB", "#BE811A", "#F77183",
//    "#EDE6E2", "#CDC6B4", "#FFE09E", "#3A7271", "#FF7B59", "#4E4E01", "#4AC684", "#8BC891",
//    "#BC8A96", "#CF6353", "#DCDE5C", "#5EAADD", "#F6A0AD", "#E269AA", "#A3DAE4", "#436E83",
//    "#002E17", "#ECFBFF", "#A1C2B6", "#50003F", "#71695B", "#67C4BB", "#536EFF", "#5D5A48",
//    "#890039", "#969381", "#371521", "#5E4665", "#AA62C3", "#8D6F81", "#2C6135", "#410601",
//    "#564620", "#E69034", "#6DA6BD", "#E58E56", "#E3A68B", "#48B176", "#D27D67", "#B5B268",
//    "#7F8427", "#FF84E6", "#435740", "#EAE408", "#F4F5FF", "#325800", "#4B6BA5", "#ADCEFF",
//    "#9B8ACC", "#885138", "#5875C1", "#7E7311", "#FEA5CA", "#9F8B5B", "#A55B54", "#89006A",
//    "#AF756F", "#2A2000", "#7499A1", "#FFB550", "#00011E", "#D1511C", "#688151", "#BC908A",
//    "#78C8EB", "#8502FF", "#483D30", "#C42221", "#5EA7FF", "#785715", "#0CEA91", "#FFFAED",
//    "#B3AF9D", "#3E3D52", "#5A9BC2", "#9C2F90", "#8D5700", "#ADD79C", "#00768B", "#337D00",
//    "#C59700", "#3156DC", "#944575", "#ECFFDC", "#D24CB2", "#97703C", "#4C257F", "#9E0366",
//    "#88FFEC", "#B56481", "#396D2B", "#56735F", "#988376", "#9BB195", "#A9795C", "#E4C5D3",
//    "#9F4F67", "#1E2B39", "#664327", "#AFCE78", "#322EDF", "#86B487", "#C23000", "#ABE86B",
//    "#96656D", "#250E35", "#A60019", "#0080CF", "#CAEFFF", "#323F61", "#A449DC", "#6A9D3B",
//    "#FF5AE4", "#636A01", "#D16CDA", "#736060", "#FFBAAD", "#D369B4", "#FFDED6", "#6C6D74",
//    "#927D5E", "#845D70", "#5B62C1", "#2F4A36", "#E45F35", "#FF3B53", "#AC84DD", "#762988",
//    "#70EC98", "#408543", "#2C3533", "#2E182D", "#323925", "#19181B", "#2F2E2C", "#023C32",
//    "#9B9EE2", "#58AFAD", "#5C424D", "#7AC5A6", "#685D75", "#B9BCBD", "#834357", "#1A7B42",
//    "#2E57AA", "#E55199", "#316E47", "#CD00C5", "#6A004D", "#7FBBEC", "#F35691", "#D7C54A",
//    "#62ACB7", "#CBA1BC", "#A28A9A", "#6C3F3B", "#FFE47D", "#DCBAE3", "#5F816D", "#3A404A",
//    "#7DBF32", "#E6ECDC", "#852C19", "#285366", "#B8CB9C", "#0E0D00", "#4B5D56", "#6B543F",
//    "#E27172", "#0568EC", "#2EB500", "#D21656", "#EFAFFF", "#682021", "#2D2011", "#DA4CFF",
//    "#70968E", "#FF7B7D", "#4A1930", "#E8C282", "#E7DBBC", "#A68486", "#1F263C", "#36574E",
//    "#52CE79", "#ADAAA9", "#8A9F45", "#6542D2", "#00FB8C", "#5D697B", "#CCD27F", "#94A5A1",
//    "#790229", "#E383E6", "#7EA4C1", "#4E4452", "#4B2C00", "#620B70", "#314C1E", "#874AA6",
//    "#E30091", "#66460A", "#EB9A8B", "#EAC3A3", "#98EAB3", "#AB9180", "#B8552F", "#1A2B2F",
//    "#94DDC5", "#9D8C76", "#9C8333", "#94A9C9", "#392935", "#8C675E", "#CCE93A", "#917100",
//    "#01400B", "#449896", "#1CA370", "#E08DA7", "#8B4A4E", "#667776", "#4692AD", "#67BDA8",
//    "#69255C", "#D3BFFF", "#4A5132", "#7E9285", "#77733C", "#E7A0CC", "#51A288", "#2C656A",
//    "#4D5C5E", "#C9403A", "#DDD7F3", "#005844", "#B4A200", "#488F69", "#858182", "#D4E9B9",
//    "#3D7397", "#CAE8CE", "#D60034", "#AA6746", "#9E5585", "#BA6200"
//).map { color(it.toHex(1, 3), it.toHex(3, 5), it.toHex(5, 7)) }.map { it to it.toHsv() }.filter { it.second.v in 0.3f..0.8f && it.second.s in 0.35f..1.0f }.map { it.first }.take(64)
//
//private fun String.toHex(start: Int, end: Int) = substring(start, end).toInt(16)
//
//private fun NPColor.toHsv(): HsvColor {
//    val min = min(r, g, b)
//    val max = max(r, g, b)
//    val delta = max - min
//    return if (delta < 0.000001f || max <= 0.0f) {
//        HsvColor(0.0f, 0.0f, max)
//    } else {
//        var h = when {
//            r >= max -> (g - b) / delta
//            g >= max -> 2.0f + (b - r) / delta
//            else -> 4.0f + (r - g) / delta
//        }
//        h *= 60.0f
//        if (h < 0.0f) {
//            h += 360.0f
//        }
//        HsvColor(h, delta / max, max)
//    }
//}
//
//private data class HsvColor(val h: Float, val s: Float, val v: Float)