package com.techvibedev.triptrace.car

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.google.android.gms.maps.model.LatLng
import com.techvibedev.triptrace.trip.trimRouteBehindPosition
import kotlin.math.cos

private const val TAG = "RouteSurfaceRenderer"

// Rough meters per degree, good enough to lay out a few km around the car
// on a flat canvas.
private const val METERS_PER_DEGREE_LAT = 110_540.0
private const val METERS_PER_DEGREE_LNG_AT_EQUATOR = 111_320.0

// Screen pixels per meter at mdpi; scaled by the car display's density so
// the same stretch of road looks the same size on any head unit.
private const val PIXELS_PER_METER_MDPI = 0.9f

// What the renderer needs from the trip each time it draws.
internal data class RouteSnapshot(
    val position: LatLng?,
    val bearingDegrees: Float?,
    val routePoints: List<LatLng>,
    val recordedPath: List<LatLng>,
    val destination: LatLng?,
)

// Draws a simple map on the car display behind the navigation card: the
// suggested route ahead, the path already driven, the destination, and an
// arrow for the car, heading-up. Plain Canvas drawing instead of Google
// Maps: the Maps SDK can't render onto Android Auto's surface, and Google's
// Navigation SDK would be a much larger dependency for a personal app.
internal class RouteSurfaceRenderer(private val carContext: CarContext) : SurfaceCallback {

    private var surfaceContainer: SurfaceContainer? = null
    private var visibleArea: Rect? = null
    private var snapshot: RouteSnapshot? = null

    // GPS bearing drops out when stopped; keep pointing where the car last
    // went instead of snapping back to north.
    private var lastBearingDegrees = 0f

    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(66, 133, 244)
    }
    private val recordedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(140, 140, 150)
    }
    private val destinationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(234, 67, 53)
    }
    private val carPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(66, 133, 244)
    }
    private val carOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        this.surfaceContainer = surfaceContainer
        render()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        this.surfaceContainer = null
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
        render()
    }

    fun update(snapshot: RouteSnapshot) {
        this.snapshot = snapshot
        render()
    }

    private fun render() {
        val container = surfaceContainer ?: return
        val surface = container.surface ?: return
        if (!surface.isValid) return
        val canvas = try {
            surface.lockCanvas(null)
        } catch (e: Exception) {
            Log.w(TAG, "Could not lock the car surface", e)
            return
        }
        try {
            draw(canvas, container)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun draw(canvas: Canvas, container: SurfaceContainer) {
        canvas.drawColor(if (carContext.isDarkMode) Color.rgb(27, 31, 36) else Color.rgb(232, 234, 237))
        val current = snapshot ?: return
        val position = current.position ?: return
        current.bearingDegrees?.let { lastBearingDegrees = it }

        val density = container.dpi / 160f
        val pixelsPerMeter = PIXELS_PER_METER_MDPI * density
        routePaint.strokeWidth = 8f * density
        recordedPaint.strokeWidth = 6f * density
        carOutlinePaint.strokeWidth = 2f * density

        // The car sits low in the part of the screen the navigation card
        // leaves free, so most of what's drawn is the road ahead.
        val area = visibleArea ?: Rect(0, 0, container.width, container.height)
        val carX = area.exactCenterX()
        val carY = area.top + area.height() * 0.7f

        val metersPerDegreeLng = METERS_PER_DEGREE_LNG_AT_EQUATOR * cos(Math.toRadians(position.latitude))
        fun project(point: LatLng): PointF {
            val eastMeters = (point.longitude - position.longitude) * metersPerDegreeLng
            val northMeters = (point.latitude - position.latitude) * METERS_PER_DEGREE_LAT
            return PointF(carX + (eastMeters * pixelsPerMeter).toFloat(), carY - (northMeters * pixelsPerMeter).toFloat())
        }

        canvas.save()
        // Heading-up: rotate the world so the car's direction points to
        // the top of the screen.
        canvas.rotate(-lastBearingDegrees, carX, carY)
        drawPolyline(canvas, current.recordedPath.map(::project), recordedPaint)
        drawPolyline(canvas, trimRouteBehindPosition(current.routePoints, position).map(::project), routePaint)
        current.destination?.let { destination ->
            val point = project(destination)
            canvas.drawCircle(point.x, point.y, 9f * density, destinationPaint)
        }
        canvas.restore()

        drawCarArrow(canvas, carX, carY, density)
    }

    private fun drawPolyline(canvas: Canvas, points: List<PointF>, paint: Paint) {
        if (points.size < 2) return
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
        canvas.drawPath(path, paint)
    }

    // Same chevron shape as the phone's ic_nav_arrow, always pointing up
    // since the map itself is rotated to the heading.
    private fun drawCarArrow(canvas: Canvas, x: Float, y: Float, density: Float) {
        val size = 14f * density
        val arrow = Path().apply {
            moveTo(x, y - size)
            lineTo(x + size * 0.75f, y + size)
            lineTo(x, y + size * 0.6f)
            lineTo(x - size * 0.75f, y + size)
            close()
        }
        canvas.drawPath(arrow, carPaint)
        canvas.drawPath(arrow, carOutlinePaint)
    }
}
