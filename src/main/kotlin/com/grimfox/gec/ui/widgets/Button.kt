package com.grimfox.gec.ui.widgets

import com.grimfox.gec.ui.widgets.HorizontalAlignment.CENTER
import com.grimfox.gec.ui.widgets.Layout.HORIZONTAL
import com.grimfox.gec.ui.widgets.Sizing.SHRINK
import com.grimfox.gec.ui.widgets.VerticalAlignment.MIDDLE
import com.grimfox.gec.util.task
import com.grimfox.logging.LOG
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT

data class ButtonStyle(
        val normal: Shape,
        val textNormal: TextStyle,
        val textShapeNormal: Shape = NO_SHAPE,
        val mouseOver: Shape,
        val textMouseOver: TextStyle,
        val textShapeMouseOver: Shape = NO_SHAPE,
        val mouseDown: Shape,
        val textMouseDown: TextStyle,
        val textShapeMouseDown: Shape = NO_SHAPE,
        val template: BlockTemplate = BlockTemplate(
                layout = HORIZONTAL),
        val textShapeTemplate: BlockTemplate = BlockTemplate(
                hAlign = CENTER,
                vAlign = MIDDLE,
                hSizing = SHRINK,
                vSizing = SHRINK)) {

    operator inline fun invoke(builder: ButtonStyle.() -> ButtonStyle): ButtonStyle {
        return this.builder()
    }
}


fun Block.button(text: Text, style: ButtonStyle, onClick: () -> Unit = {}): Block {
    text.style = style.textNormal
    return block {
        val mainBlock = this
        var mouseDownOver = false
        var mouseOver = false
        isVisible = style.template.isVisible
        hAlign = style.template.hAlign
        vAlign = style.template.vAlign
        layout = style.template.layout
        xOffset = style.template.xOffset
        yOffset = style.template.yOffset
        hSizing = style.template.hSizing
        width = style.template.width
        vSizing = style.template.vSizing
        height = style.template.height
        padLeft = style.template.padLeft
        padRight = style.template.padRight
        padTop = style.template.padTop
        padBottom = style.template.padBottom
        shape = style.normal
        val textShape = block {
            isVisible = style.textShapeTemplate.isVisible
            hAlign = style.textShapeTemplate.hAlign
            vAlign = style.textShapeTemplate.vAlign
            layout = style.textShapeTemplate.layout
            xOffset = style.textShapeTemplate.xOffset
            yOffset = style.textShapeTemplate.yOffset
            hSizing = style.textShapeTemplate.hSizing
            width = style.textShapeTemplate.width
            vSizing = style.textShapeTemplate.vSizing
            height = style.textShapeTemplate.height
            padLeft = style.textShapeTemplate.padLeft
            padRight = style.textShapeTemplate.padRight
            padTop = style.textShapeTemplate.padTop
            padBottom = style.textShapeTemplate.padBottom
            this.text = text
            isMouseAware = false
        }
        onMouseOver {
            mouseOver = true
            if (!mouseDownOver) {
                mainBlock.shape = style.mouseOver
                text.style = style.textMouseOver
                textShape.shape = style.textShapeMouseOver
            }
        }
        onMouseOut {
            mouseOver = false
            if (!mouseDownOver) {
                mainBlock.shape = style.normal
                text.style = style.textNormal
                textShape.shape = style.textShapeNormal
            }
        }
        onMouseDown { button, _, _, _ ->
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                mouseDownOver = true
                mainBlock.shape = style.mouseDown
                text.style = style.textMouseDown
                textShape.shape = style.textShapeMouseDown
            }
        }
        onMouseRelease { button, _, _, _ ->
            if (button == GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                mouseDownOver = false
                mainBlock.shape = if (mouseOver) style.mouseOver else style.normal
                text.style = if (mouseOver) style.textMouseOver else style.textNormal
                textShape.shape = if (mouseOver) style.textShapeMouseOver else style.textShapeNormal
            }
        }
        onMouseClick { button, _, _, _ ->
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                task {
                    try {
                        onClick()
                    } catch (t: Throwable) {
                        LOG.error("Error executing button task.", t)
                    }
                }
            }
        }
    }
}

fun Block.button(builder: Block.() -> Unit, style: ButtonStyle, onClick: () -> Unit = {}): Block {
    return block {
        val mainBlock = this
        var mouseDownOver = false
        var mouseOver = false
        isVisible = style.template.isVisible
        hAlign = style.template.hAlign
        vAlign = style.template.vAlign
        layout = style.template.layout
        xOffset = style.template.xOffset
        yOffset = style.template.yOffset
        hSizing = style.template.hSizing
        width = style.template.width
        vSizing = style.template.vSizing
        height = style.template.height
        padLeft = style.template.padLeft
        padRight = style.template.padRight
        padTop = style.template.padTop
        padBottom = style.template.padBottom
        shape = style.normal
        val textShape = block {
            isVisible = style.textShapeTemplate.isVisible
            hAlign = style.textShapeTemplate.hAlign
            vAlign = style.textShapeTemplate.vAlign
            layout = style.textShapeTemplate.layout
            xOffset = style.textShapeTemplate.xOffset
            yOffset = style.textShapeTemplate.yOffset
            hSizing = style.textShapeTemplate.hSizing
            width = style.textShapeTemplate.width
            vSizing = style.textShapeTemplate.vSizing
            height = style.textShapeTemplate.height
            padLeft = style.textShapeTemplate.padLeft
            padRight = style.textShapeTemplate.padRight
            padTop = style.textShapeTemplate.padTop
            padBottom = style.textShapeTemplate.padBottom
            builder()
            isMouseAware = false
        }
        onMouseOver {
            mouseOver = true
            if (!mouseDownOver) {
                mainBlock.shape = style.mouseOver
                textShape.layoutChildren.forEach { it.text.style = style.textMouseOver }
                textShape.shape = style.textShapeMouseOver
            }
        }
        onMouseOut {
            mouseOver = false
            if (!mouseDownOver) {
                mainBlock.shape = style.normal
                textShape.layoutChildren.forEach { it.text.style = style.textNormal }
                textShape.shape = style.textShapeNormal
            }
        }
        onMouseDown { button, _, _, _ ->
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                mouseDownOver = true
                mainBlock.shape = style.mouseDown
                textShape.layoutChildren.forEach { it.text.style = style.textMouseDown }
                textShape.shape = style.textShapeMouseDown
            }
        }
        onMouseRelease { button, _, _, _ ->
            if (button == GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                mouseDownOver = false
                mainBlock.shape = if (mouseOver) style.mouseOver else style.normal
                textShape.layoutChildren.forEach { it.text.style = if (mouseOver) style.textMouseOver else style.textNormal }
                textShape.shape = if (mouseOver) style.textShapeMouseOver else style.textShapeNormal
            }
        }
        onMouseClick { button, _, _, _ ->
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                task {
                    try {
                        onClick()
                    } catch (t: Throwable) {
                        LOG.error("Error executing button task.", t)
                    }
                }
            }
        }
    }
}
