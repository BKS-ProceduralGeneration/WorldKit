package com.grimfox.gec.filter

import com.grimfox.gec.Main
import com.grimfox.gec.generator.Point
import com.grimfox.gec.model.ClosestPoints
import com.grimfox.gec.model.ClosestPointsMatrix
import com.grimfox.gec.model.DataFiles
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.util.Utils.pow
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File
import java.util.*

@Command(name = "closest-points", description = "Create a matrix of the closest points to each pixel from points.")
class ClosestPointsFilter : Runnable {

    @Option(name = arrayOf("-i", "--input"), description = "The data file to read as input.", required = true)
    var inputFile: File = File(Main.workingDir, "input.bin")

    @Option(name = arrayOf("-e", "--exponent"), description = "The size of the output as an exponent of 2. The output width and height will be equal to 2 to the power of size.", required = false)
    var exponent: Int? = null

    @Option(name = arrayOf("-p", "--point-count"), description = "The number of closest points to output per fragment.", required = false)
    var pointCount: Int = 2

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    @Option(name = arrayOf("-w", "--wrap"), description = "Whether or not the points should wrap at the edges.", required = false)
    var wrapEdges: Boolean = false

    override fun run() {
        DataFiles.openAndUse<Point>(inputFile) { points ->
            val outputExponent = exponent ?: points.exponent + 3
            val outputWidth = 2.pow(outputExponent)
            val gridStride = points.width
            val gridSquareSize = outputWidth / gridStride
            val pointWrapOffset = outputWidth.toFloat()
            val pointsPerFragment = Math.min(Math.max(pointCount, 2), 5)
            fun <M : ClosestPointsMatrix> execute(format: Class<M>) {
                DataFiles.createAndUse(outputFile, outputExponent, format) { heightMap ->
                    val end = heightMap.width - 1
                    for (y in 0..end) {
                        for (x in 0..end) {
                            val gridX = x / gridSquareSize
                            val gridY = y / gridSquareSize
                            val closestPoints = getClosestPoints(points, gridX, gridY, gridStride, pointWrapOffset, Point(x.toFloat(), y.toFloat()), outputWidth)
                            heightMap[x, y] = closestPoints
                        }
                    }
                }
            }
            when (pointsPerFragment) {
                3 -> execute(ClosestPointsMatrix.M3::class.java)
                4 -> execute(ClosestPointsMatrix.M4::class.java)
                5 -> execute(ClosestPointsMatrix.M5::class.java)
                else -> execute(ClosestPointsMatrix.M2::class.java)
            }
        }
    }

    private fun getClosestPoints(points: Matrix<Point>, x: Int, y: Int, gridStride: Int, pointWrapOffset: Float, point: Point, outputWidth: Int): ClosestPoints {
        val closestPoints = ArrayList<Pair<Int, Float>>(49)
        for (yOff in -3..3) {
            for (xOff in -3..3) {
                var ox = x + xOff
                var oy = y + yOff
                var xDistAdjust = 0.0f
                var yDistAdjust = 0.0f
                if (wrapEdges) {
                    val ox1 = ox
                    ox = (ox + gridStride) % gridStride
                    if (ox1 > ox) {
                        xDistAdjust = pointWrapOffset
                    } else if (ox1 < ox) {
                        xDistAdjust = -pointWrapOffset
                    }
                    val oy1 = oy
                    oy = (oy + gridStride) % gridStride
                    if (oy1 > oy) {
                        yDistAdjust = pointWrapOffset
                    } else if (oy1 < oy) {
                        yDistAdjust = -pointWrapOffset
                    }
                }
                if (oy >= 0 && oy < gridStride && ox >= 0 && ox < gridStride) {
                    val index = oy * gridStride + ox
                    val other = points[ox, oy]
                    val distance = point.distanceSquaredTo(Point(other.x * outputWidth + xDistAdjust, other.y * outputWidth + yDistAdjust))
                    closestPoints.add(Pair(index, distance))
                }
            }
        }
        closestPoints.sort { p1, p2 ->
            p1.second.compareTo(p2.second)
        }
        return ClosestPoints(closestPoints[0],
                closestPoints[1],
                closestPoints[2],
                closestPoints[3],
                closestPoints[4])
    }
}
