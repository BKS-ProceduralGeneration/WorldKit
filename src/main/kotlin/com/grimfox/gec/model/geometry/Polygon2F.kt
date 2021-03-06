package com.grimfox.gec.model.geometry

import java.util.*

class Polygon2F(val points: List<Point2F>, val isClosed: Boolean) {

    companion object {

        fun fromUnsortedEdges(edges: Collection<LineSegment2F>, splices: ArrayList<Pair<LineSegment2F, Point2F>>? = null, reusePoints: Boolean = false, epsilon: Float = 0.000001f): Polygon2F {
            val polygons = ArrayList<Polygon2F>()
            val mutableEdges = ArrayList(edges)
            while (mutableEdges.isNotEmpty()) {
                val border = ArrayList<Point2F>(mutableEdges.size + 1)
                val seedEdge = mutableEdges.removeAt(0)
                border.add(seedEdge.a)
                splices?.forEach {
                    if (it.first.epsilonEquals(seedEdge, epsilon)) {
                        border.add(it.second)
                    }
                }
                border.add(seedEdge.b)
                var currentEdge = seedEdge
                while (mutableEdges.isNotEmpty()) {
                    var nextEdge: LineSegment2F? = null
                    for (i in 0..mutableEdges.size - 1) {
                        if (currentEdge.a.epsilonEquals(mutableEdges[i].b, epsilon)) {
                            nextEdge = mutableEdges.removeAt(i)
                            break
                        }
                    }
                    if (nextEdge == null) {
                        break
                    }
                    splices?.forEach {
                        val finalNextEdge = nextEdge
                        if (finalNextEdge != null) {
                            if (it.first.epsilonEquals(finalNextEdge, epsilon)) {
                                border.add(0, it.second)
                            }
                        }
                    }
                    border.add(0, nextEdge.a)
                    currentEdge = nextEdge
                }
                currentEdge = seedEdge
                while (mutableEdges.isNotEmpty()) {
                    var nextEdge: LineSegment2F? = null
                    for (i in 0..mutableEdges.size - 1) {
                        if (currentEdge.b.epsilonEquals(mutableEdges[i].a, epsilon)) {
                            nextEdge = mutableEdges.removeAt(i)
                            break
                        }
                    }
                    if (nextEdge == null) {
                        break
                    }
                    splices?.forEach {
                        val finalNextEdge = nextEdge
                        if (finalNextEdge != null) {
                            if (it.first.epsilonEquals(finalNextEdge, epsilon)) {
                                border.add(it.second)
                            }
                        }
                    }
                    border.add(nextEdge.b)
                    currentEdge = nextEdge
                }
                var isClosed = false
                if (border.first() == border.last()) {
                    border.removeAt(border.size - 1)
                    isClosed = true
                }
                polygons.add(Polygon2F(border, isClosed))
            }
            if (polygons.size == 1 || !reusePoints) {
                return polygons.first()
            } else {
                polygons.sortByDescending { it.points.size }
                var mainPoly = polygons.removeAt(0)
                for (i in 0..polygons.size - 1) {
                    val otherPoly = polygons.removeAt(0)
                    if (!otherPoly.isClosed) {
                        continue
                    }
                    var pointInCommon: Pair<Int, Int>? = null
                    var tooMany = false
                    for ((j, mainPoint) in mainPoly.points.withIndex()) {
                        for ((k, otherPoint) in otherPoly.points.withIndex()) {
                            if (mainPoint.epsilonEquals(otherPoint, epsilon)) {
                                if (pointInCommon != null) {
                                    tooMany = true
                                }
                                pointInCommon = Pair(j, k)
                            }
                            if (tooMany) {
                                break
                            }
                        }
                        if (tooMany) {
                            break
                        }
                    }
                    if (pointInCommon == null || tooMany) {
                        continue
                    }
                    mainPoly = joinPolygons(mainPoly, otherPoly, pointInCommon)
                }
                return mainPoly
            }
        }

        private fun joinPolygons(p1: Polygon2F, p2: Polygon2F, pointInCommon: Pair<Int, Int>): Polygon2F {
            val combined = ArrayList(p1.points.subList(0, pointInCommon.first))
            val rotated = ArrayList(p2.points)
            Collections.rotate(rotated, -pointInCommon.second)
            combined.addAll(rotated)
            combined.addAll(p1.points.subList(pointInCommon.first, p1.points.size))
            return Polygon2F(combined, true)
        }
    }

    val bounds: Bounds2F by lazy {
        var xMin = Float.MAX_VALUE
        var xMax = -Float.MAX_VALUE
        var yMin = Float.MAX_VALUE
        var yMax = -Float.MAX_VALUE
        points.forEach {
            if (it.x < xMin) {
                xMin = it.x
            }
            if (it.x > xMax) {
                xMax = it.x
            }
            if (it.y < yMin) {
                yMin = it.y
            }
            if (it.y > yMax) {
                yMax = it.y
            }
        }
        Bounds2F(Point2F(xMin, yMin), Point2F(xMax, yMax))
    }

    val edges: List<LineSegment2F> by lazy {
        val edges = ArrayList<LineSegment2F>()
        for (i in 1..points.size - if (isClosed) 0 else 1) {
            edges.add(LineSegment2F(points[i - 1], points[i % points.size]))
        }
        edges
    }

    fun split(chunkSize: Int): MutableList<Polygon2F> {
        if ((isClosed && points.size <= chunkSize) || (!isClosed && points.size <= chunkSize + 1)) {
            return arrayListOf(this)
        }
        val polygons = ArrayList<Polygon2F>()
        var localPoints = ArrayList<Point2F>()
        for (i in 0..points.size - if (isClosed) 0 else 1) {
            val point = points[i % points.size]
            localPoints.add(point)
            if (localPoints.size == chunkSize + 1) {
                polygons.add(Polygon2F(localPoints, false))
                localPoints = ArrayList<Point2F>()
                localPoints.add(point)
            }
        }
        if (localPoints.size > 1) {
            polygons.add(Polygon2F(localPoints, false))
        }
        return polygons
    }

    fun doesEdgeIntersect(edge: LineSegment2F, vararg exclusions: Point2F): Pair<Boolean, Int> {
        if (!bounds.isWithin(edge)) {
            return Pair(false, -1)
        } else {
            for (i in 0..edges.size - 1) {
                val otherEdge = edges[i]
                var excludeEdge = false
                for (point in exclusions) {
                    if (otherEdge.a.epsilonEquals(point) || otherEdge.b.epsilonEquals(point)) {
                        excludeEdge = true
                        break
                    }
                }
                if (excludeEdge) {
                    continue
                }
                if (edge.intersectsOrTouches(otherEdge)) {
                    return Pair(true, i)
                }
            }
        }
        return Pair(false, -1)
    }

    fun doesEdgeIntersect(edge: LineSegment2F, start: Int): Pair<Boolean, Int> {
        for (i in start..edges.size - 1) {
            val it = edges[i]
            if (edge.intersectsOrTouches(it)) {
                return Pair(true, i)
            }
        }
        return Pair(false, -1)
    }

    fun doesEdgeIntersect(other: Polygon2F): Triple<Boolean, Int, Int> {
        edges.forEachIndexed { i, it ->
            val (intersects, j) = other.doesEdgeIntersect(it)
            if (intersects) {
                return Triple(true, i, j)
            }
        }
        return Triple(false, -1, -1)
    }

    fun distance2(point: Point2F): Float {
        var minDist = Float.MAX_VALUE
        edges.forEach {
            val localDist = it.distance2(point)
            if (localDist == 0.0f) {
                return localDist
            }
            if (localDist < minDist) {
                minDist = localDist
            }
        }
        return minDist
    }

    fun distance2(edge: LineSegment2F): Float {
        var minDist = Float.MAX_VALUE
        edges.forEach {
            val localDist = edge.distance2(it)
            if (localDist == 0.0f) {
                return localDist
            }
            if (localDist < minDist) {
                minDist = localDist
            }
        }
        return minDist
    }

    fun isWithin(point: Point2F): Boolean {
        if (!bounds.isWithin(point)) {
            return false
        }
        var i: Int = 0
        var j: Int = points.size - 1
        var c = false
        while (i < points.size) {
            val pi = points[i]
            val pj = points[j]
            if (((pi.y > point.y) != (pj.y > point.y)) && (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x)) {
                c = !c
            }
            j = i
            i++
        }
        return c
    }
}