package com.example.ui

import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.example.data.SurveyorItemEntity
import com.example.sim.HeadlightState
import com.example.sim.PooledParticle
import com.example.sim.QualityPreset
import com.example.sim.SignalAspect
import com.example.sim.TimeOfDayMode
import com.example.sim.WeatherMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class CameraViewMode(val label: String) {
    CHASE_CAM("Chase 3D"),
    CAB_VIEW("Cab 3D"),
    FREE_ORBIT("Orbit 3D"),
    TRACKSIDE_CAM("Trackside 3D"),
    STATION_FLYBY("Fly-By 3D"),
    WEBGL_SINGLE_FILE("Chase 3D")
}

// ============================================================================
// TRUE 3D MATH, PERSPECTIVE CAMERA (FOV 65), MESH PRIMITIVES & PBR LIGHTING
// ============================================================================

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-5f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }
}

private data class Pose3D(
    val pos: Vec3,
    val right: Vec3,
    val up: Vec3,
    val forward: Vec3
) {
    fun toWorld(local: Vec3): Vec3 = Vec3(
        pos.x + right.x * local.x + up.x * local.y + forward.x * local.z,
        pos.y + right.y * local.x + up.y * local.y + forward.y * local.z,
        pos.z + right.z * local.x + up.z * local.y + forward.z * local.z
    )

    fun dirToWorld(localDir: Vec3): Vec3 = Vec3(
        right.x * localDir.x + up.x * localDir.y + forward.x * localDir.z,
        right.y * localDir.x + up.y * localDir.y + forward.y * localDir.z,
        right.z * localDir.x + up.z * localDir.y + forward.z * localDir.z
    ).normalized()
}

private class PerspectiveCamera3D(
    val pos: Vec3,
    target: Vec3,
    worldUp: Vec3 = Vec3(0f, 1f, 0f),
    val fovDeg: Float = 65f,
    val width: Float,
    val height: Float,
    val near: Float = 0.6f,
    val far: Float = 900f
) {
    val forward: Vec3 = (target - pos).normalized()
    val right: Vec3 = forward.cross(worldUp).normalized().let {
        if (it.length() < 0.01f) Vec3(1f, 0f, 0f) else it
    }
    val up: Vec3 = right.cross(forward).normalized()
    val focalLength: Float = (height * 0.5f) / tan(Math.toRadians((fovDeg * 0.5f).toDouble())).toFloat()
    val cx: Float = width * 0.5f
    val cy: Float = height * 0.5f

    fun worldToCamera(w: Vec3): Vec3 {
        val rel = w - pos
        return Vec3(rel.dot(right), rel.dot(up), rel.dot(forward))
    }

    fun projectCameraPoint(camPt: Vec3): Offset {
        val invZ = focalLength / max(near, camPt.z)
        return Offset(cx + camPt.x * invZ, cy - camPt.y * invZ)
    }
}

private class Face3D(
    val v0: Vec3,
    val v1: Vec3,
    val v2: Vec3,
    val v3: Vec3? = null,
    val normal: Vec3,
    val baseColor: Color,
    val metalness: Float = 0.1f,
    val roughness: Float = 0.8f,
    val emissive: Boolean = false,
    val isShadow: Boolean = false,
    val alpha: Float = 1f,
    val depthBias: Float = 0f
)

private class ProjectedPoly(
    val pts: FloatArray, // [x0, y0, x1, y1, ...]
    val count: Int,
    val sortDepth: Float,
    val shadedColor: Color,
    val strokeColor: Color? = null
)

// ============================================================================
// 3D SPLINE ROUTE & 3D ROLLING HILLS TERRAIN FUNCTIONS
// ============================================================================

private fun evalTrackCenter(distMeters: Double): Vec3 {
    val z = distMeters.toFloat()
    val x = (sin(distMeters * 0.0042) * 84.0 + cos(distMeters * 0.0019) * 48.0 - (sin(0.0) * 84.0 + cos(0.0) * 48.0)).toFloat()
    val y = (sin(distMeters * 0.0034) * 7.8 + cos(distMeters * 0.0072) * 3.2 - 3.2).toFloat()
    return Vec3(x, y, z)
}

private fun evalTrackPose(distMeters: Double): Pose3D {
    val p0 = evalTrackCenter(distMeters)
    val p1 = evalTrackCenter(distMeters + 1.5)
    val forward = (p1 - p0).normalized()
    val worldUp = Vec3(0f, 1f, 0f)
    val right = forward.cross(worldUp).normalized()
    val up = right.cross(forward).normalized()
    return Pose3D(p0, right, up, forward)
}

private fun evalHillHeight(wx: Float, wz: Float): Float {
    val trackPt = evalTrackCenter(wz.toDouble())
    val distFromTrack = abs(wx - trackPt.x)
    val naturalHill = (
        sin(wx * 0.014f) * cos(wz * 0.011f) * 24f +
            sin(wx * 0.028f + wz * 0.019f) * 11f +
            cos(wx * 0.052f - wz * 0.034f) * 4.2f +
            6.5f
        )
    // Carve flat railroad cut / embankment smoothly around the 3D track spline
    val cutWidth = 7.5f
    val blendWidth = 34f
    return if (distFromTrack <= cutWidth) {
        trackPt.y - 0.38f
    } else if (distFromTrack < blendWidth) {
        val t = ((distFromTrack - cutWidth) / (blendWidth - cutWidth)).coerceIn(0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        val sideHill = naturalHill + if (wx > trackPt.x) 6.5f else 4.5f
        (trackPt.y - 0.38f) * (1f - smooth) + sideHill * smooth
    } else {
        naturalHill + if (wx > trackPt.x) 6.5f else 4.5f
    }
}

// ============================================================================
// PBR-STYLE 3D SHADING (HEMISPHERE + DIRECTIONAL SUN + METALLIC SPECULAR + FOG)
// ============================================================================

private fun shade3DFace(
    normal: Vec3,
    worldCenter: Vec3,
    baseColor: Color,
    metalness: Float,
    roughness: Float,
    emissive: Boolean,
    isShadow: Boolean,
    alpha: Float,
    camPos: Vec3,
    sunDir: Vec3,
    sunColor: Color,
    skyAmbient: Color,
    groundAmbient: Color,
    fogColor: Color
): Color {
    if (isShadow) {
        return Color(0xFF070D19).copy(alpha = alpha)
    }
    if (emissive) {
        return baseColor.copy(alpha = alpha)
    }

    // 1. Hemisphere Light (sky to ground ambient illumination)
    val hemiWeight = (normal.y * 0.5f + 0.5f).coerceIn(0f, 1f)
    val ambR = groundAmbient.red * (1f - hemiWeight) + skyAmbient.red * hemiWeight
    val ambG = groundAmbient.green * (1f - hemiWeight) + skyAmbient.green * hemiWeight
    val ambB = groundAmbient.blue * (1f - hemiWeight) + skyAmbient.blue * hemiWeight

    // 2. Directional Sun Light (Lambert diffuse + Blinn-Phong metallic specular)
    val nDotL = max(0f, normal.dot(sunDir))
    val viewDir = (camPos - worldCenter).normalized()
    val halfVec = (sunDir + viewDir).normalized()
    val nDotH = max(0f, normal.dot(halfVec))
    val shininess = (2f / max(0.08f, roughness * roughness)) - 2f
    val specPower = nDotH.pow(shininess.coerceIn(4f, 96f))
    val specStrength = specPower * (0.18f + metalness * 0.85f) * (0.3f + 0.7f * nDotL)

    val diffuseFactor = 0.36f + nDotL * 0.82f
    var r = baseColor.red * (ambR * 0.55f + sunColor.red * diffuseFactor * 0.72f) + sunColor.red * specStrength
    var g = baseColor.green * (ambG * 0.55f + sunColor.green * diffuseFactor * 0.72f) + sunColor.green * specStrength
    var b = baseColor.blue * (ambB * 0.55f + sunColor.blue * diffuseFactor * 0.72f) + sunColor.blue * specStrength

    // 3. Exponential Distance Fog (THREE.FogExp2 equivalent)
    val dist = (worldCenter - camPos).length()
    val fogFactor = (1f - kotlin.math.exp((-dist * 0.0022f).toDouble()).toFloat()).coerceIn(0f, 0.82f)
    r = r * (1f - fogFactor) + fogColor.red * fogFactor
    g = g * (1f - fogFactor) + fogColor.green * fogFactor
    b = b * (1f - fogFactor) + fogColor.blue * fogFactor

    return Color(
        red = r.coerceIn(0f, 1f),
        green = g.coerceIn(0f, 1f),
        blue = b.coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0f, 1f)
    )
}

// ============================================================================
// 3D PRIMITIVE BUILDERS (BOXGEOMETRY, CYLINDERGEOMETRY, CONEGEOMETRY, SHADOWS)
// ============================================================================

private fun addBox3D(
    outFaces: MutableList<Face3D>,
    pose: Pose3D,
    localCenter: Vec3,
    sizeX: Float,
    sizeY: Float,
    sizeZ: Float,
    color: Color,
    metalness: Float = 0.45f,
    roughness: Float = 0.35f,
    emissive: Boolean = false,
    depthBias: Float = 0f
) {
    val hx = sizeX * 0.5f
    val hy = sizeY * 0.5f
    val hz = sizeZ * 0.5f

    val c000 = pose.toWorld(localCenter + Vec3(-hx, -hy, -hz))
    val c100 = pose.toWorld(localCenter + Vec3(hx, -hy, -hz))
    val c110 = pose.toWorld(localCenter + Vec3(hx, hy, -hz))
    val c010 = pose.toWorld(localCenter + Vec3(-hx, hy, -hz))
    val c001 = pose.toWorld(localCenter + Vec3(-hx, -hy, hz))
    val c101 = pose.toWorld(localCenter + Vec3(hx, -hy, hz))
    val c111 = pose.toWorld(localCenter + Vec3(hx, hy, hz))
    val c011 = pose.toWorld(localCenter + Vec3(-hx, hy, hz))

    // Top (+Y)
    outFaces.add(
        Face3D(c010, c110, c111, c011, pose.up, color, metalness, roughness, emissive, depthBias = depthBias)
    )
    // Front (+Z)
    outFaces.add(
        Face3D(c001, c101, c111, c011, pose.forward, color, metalness, roughness, emissive, depthBias = depthBias)
    )
    // Back (-Z)
    outFaces.add(
        Face3D(c100, c000, c010, c110, pose.forward * -1f, color, metalness, roughness, emissive, depthBias = depthBias)
    )
    // Right (+X)
    outFaces.add(
        Face3D(c100, c101, c111, c110, pose.right, color, metalness, roughness, emissive, depthBias = depthBias)
    )
    // Left (-X)
    outFaces.add(
        Face3D(c001, c000, c010, c011, pose.right * -1f, color, metalness, roughness, emissive, depthBias = depthBias)
    )
}

/**
 * Vertical 3D Cylinder primitive (`THREE.CylinderGeometry` along local Y axis).
 */
private fun addVerticalCylinder3D(
    outFaces: MutableList<Face3D>,
    pose: Pose3D,
    localCenter: Vec3,
    radiusTop: Float,
    radiusBottom: Float,
    height: Float,
    segments: Int,
    color: Color,
    metalness: Float = 0.5f,
    roughness: Float = 0.35f,
    emissive: Boolean = false,
    depthBias: Float = 0f
) {
    val hy = height * 0.5f
    val topCenter = pose.toWorld(localCenter + Vec3(0f, hy, 0f))
    for (i in 0 until segments) {
        val a0 = (i.toFloat() / segments) * (2f * PI.toFloat())
        val a1 = ((i + 1).toFloat() / segments) * (2f * PI.toFloat())
        val am = (a0 + a1) * 0.5f

        val p0Bot = pose.toWorld(localCenter + Vec3(cos(a0) * radiusBottom, -hy, sin(a0) * radiusBottom))
        val p1Bot = pose.toWorld(localCenter + Vec3(cos(a1) * radiusBottom, -hy, sin(a1) * radiusBottom))
        val p1Top = pose.toWorld(localCenter + Vec3(cos(a1) * radiusTop, hy, sin(a1) * radiusTop))
        val p0Top = pose.toWorld(localCenter + Vec3(cos(a0) * radiusTop, hy, sin(a0) * radiusTop))

        val radialNormal = pose.dirToWorld(Vec3(cos(am), 0.15f, sin(am)))
        outFaces.add(
            Face3D(p0Bot, p1Bot, p1Top, p0Top, radialNormal, color, metalness, roughness, emissive, depthBias = depthBias)
        )
        outFaces.add(
            Face3D(topCenter, p0Top, p1Top, null, pose.up, color, metalness, roughness, emissive, depthBias = depthBias)
        )
    }
}

/**
 * 3D Cone primitive (`THREE.ConeGeometry` along world/local Y axis) for 3D pine trees.
 */
private fun addCone3D(
    outFaces: MutableList<Face3D>,
    worldBaseCenter: Vec3,
    radius: Float,
    height: Float,
    segments: Int,
    color: Color
) {
    val apex = worldBaseCenter + Vec3(0f, height, 0f)
    for (i in 0 until segments) {
        val a0 = (i.toFloat() / segments) * (2f * PI.toFloat())
        val a1 = ((i + 1).toFloat() / segments) * (2f * PI.toFloat())
        val am = (a0 + a1) * 0.5f

        val p0 = worldBaseCenter + Vec3(cos(a0) * radius, 0f, sin(a0) * radius)
        val p1 = worldBaseCenter + Vec3(cos(a1) * radius, 0f, sin(a1) * radius)
        val normal = Vec3(cos(am) * 0.86f, 0.5f, sin(am) * 0.86f).normalized()
        outFaces.add(
            Face3D(
                v0 = p0,
                v1 = p1,
                v2 = apex,
                v3 = null,
                normal = normal,
                baseColor = color,
                metalness = 0.04f,
                roughness = 0.82f
            )
        )
    }
}

/**
 * Rotating 3D Locomotive/Car Wheel (`THREE.CylinderGeometry` along local X axle axis + flange + rotating spokes).
 */
private fun addWheelCylinder3D(
    outFaces: MutableList<Face3D>,
    pose: Pose3D,
    localCenter: Vec3,
    radius: Float,
    width: Float,
    wheelRotRad: Float,
    steelColor: Color,
    darkColor: Color
) {
    val segments = 10
    val hx = width * 0.5f
    val outerX = if (localCenter.x >= 0f) hx else -hx
    val outerNormal = if (localCenter.x >= 0f) pose.right else (pose.right * -1f)
    val wheelFaceCenter = pose.toWorld(localCenter + Vec3(outerX, 0f, 0f))

    for (i in 0 until segments) {
        val a0 = wheelRotRad + (i.toFloat() / segments) * (2f * PI.toFloat())
        val a1 = wheelRotRad + ((i + 1).toFloat() / segments) * (2f * PI.toFloat())
        val am = (a0 + a1) * 0.5f

        val y0 = sin(a0) * radius
        val z0 = cos(a0) * radius
        val y1 = sin(a1) * radius
        val z1 = cos(a1) * radius

        val pIn0 = pose.toWorld(localCenter + Vec3(-hx, y0, z0))
        val pOut0 = pose.toWorld(localCenter + Vec3(hx, y0, z0))
        val pOut1 = pose.toWorld(localCenter + Vec3(hx, y1, z1))
        val pIn1 = pose.toWorld(localCenter + Vec3(-hx, y1, z1))

        val treadNormal = pose.dirToWorld(Vec3(0f, sin(am), cos(am)))
        // Cylindrical tread rim
        outFaces.add(
            Face3D(pIn0, pOut0, pOut1, pIn1, treadNormal, steelColor, metalness = 0.88f, roughness = 0.20f, depthBias = -0.15f)
        )

        // Outer circular wheel disk segment (alternating shade so rotation is unmistakably visible!)
        val facePoint0 = pose.toWorld(localCenter + Vec3(outerX, y0 * 0.92f, z0 * 0.92f))
        val facePoint1 = pose.toWorld(localCenter + Vec3(outerX, y1 * 0.92f, z1 * 0.92f))
        val spokeShade = if (i % 2 == 0) steelColor else darkColor
        outFaces.add(
            Face3D(
                wheelFaceCenter,
                facePoint0,
                facePoint1,
                null,
                outerNormal,
                spokeShade,
                metalness = 0.82f,
                roughness = 0.25f,
                depthBias = -0.22f
            )
        )
    }

    // Rotating 3D Cross Spoke Bar on the outer wheel face
    val sCos = cos(wheelRotRad)
    val sSin = sin(wheelRotRad)
    val barLen = radius * 0.82f
    val barThick = radius * 0.16f
    val b0 = pose.toWorld(localCenter + Vec3(outerX * 1.08f, -sSin * barLen - sCos * barThick, -sCos * barLen + sSin * barThick))
    val b1 = pose.toWorld(localCenter + Vec3(outerX * 1.08f, -sSin * barLen + sCos * barThick, -sCos * barLen - sSin * barThick))
    val b2 = pose.toWorld(localCenter + Vec3(outerX * 1.08f, sSin * barLen + sCos * barThick, sCos * barLen - sSin * barThick))
    val b3 = pose.toWorld(localCenter + Vec3(outerX * 1.08f, sSin * barLen - sCos * barThick, sCos * barLen + sSin * barThick))
    outFaces.add(
        Face3D(b0, b1, b2, b3, outerNormal, Color(0xFFE2E8F0), metalness = 0.9f, roughness = 0.18f, depthBias = -0.3f)
    )
}

/**
 * Projects a real-time 3D planar ground shadow of a 3D bounding box onto the ground/ballast along `sunDir`.
 */
private fun addCastShadow3D(
    outFaces: MutableList<Face3D>,
    pose: Pose3D,
    localCenter: Vec3,
    sizeX: Float,
    sizeY: Float,
    sizeZ: Float,
    groundY: Float,
    sunDir: Vec3
) {
    val hx = sizeX * 0.5f
    val hy = sizeY * 0.5f
    val hz = sizeZ * 0.5f
    val topCorners = arrayOf(
        pose.toWorld(localCenter + Vec3(-hx, hy, -hz)),
        pose.toWorld(localCenter + Vec3(hx, hy, -hz)),
        pose.toWorld(localCenter + Vec3(hx, hy, hz)),
        pose.toWorld(localCenter + Vec3(-hx, hy, hz))
    )
    val sunY = max(0.25f, sunDir.y)
    val projected = Array(4) { idx ->
        val c = topCorners[idx]
        val dy = max(0f, c.y - groundY)
        val t = dy / sunY
        Vec3(c.x - sunDir.x * t, groundY + 0.03f, c.z - sunDir.z * t)
    }
    outFaces.add(
        Face3D(
            v0 = projected[0],
            v1 = projected[1],
            v2 = projected[2],
            v3 = projected[3],
            normal = Vec3(0f, 1f, 0f),
            baseColor = Color.Black,
            isShadow = true,
            alpha = 0.38f,
            depthBias = 0.4f
        )
    )
}

// ============================================================================
// DETAILED 3D LOCOMOTIVE & FREIGHT CONSIST ASSEMBLER
// ============================================================================

private fun buildDetailed3DLocomotive(
    outFaces: MutableList<Face3D>,
    pose: Pose3D,
    wheelRotationRad: Float,
    customLiveryColor: Color?,
    headlightState: HeadlightState,
    sunDir: Vec3
) {
    val yellowBody = customLiveryColor ?: Color(0xFFF59E0B) // Bright Metallic Amber-Yellow
    val blueStripe = Color(0xFF0284C7) // Royal Heritage Blue
    val darkChassis = Color(0xFF1E293B) // Dark Metallic Steel
    val silverRoof = Color(0xFFD1D5DB) // Brushed Aluminum Roof
    val darkWindow = Color(0xFF081120) // Reflective Dark Cab Glass
    val wheelSteel = Color(0xFF94A3B8) // Machined Steel Wheels
    val handrailGold = Color(0xFFFDE047)

    // 1. Real-time 3D shadow cast onto the track bed
    addCastShadow3D(
        outFaces = outFaces,
        pose = pose,
        localCenter = Vec3(0f, 2.2f, 0f),
        sizeX = 3.25f,
        sizeY = 3.8f,
        sizeZ = 16.6f,
        groundY = pose.pos.y + 0.04f,
        sunDir = sunDir
    )

    // 2. Front & Rear 3D Bogies (Trucks) + 8 Rotating 3D Cylinder Wheels (4 per bogie)
    val bogieCentersZ = floatArrayOf(4.85f, -4.85f)
    val axleOffsetsZ = floatArrayOf(-1.12f, 1.12f)
    for (bz in bogieCentersZ) {
        // Truck bolster & sideframes
        addBox3D(outFaces, pose, Vec3(-0.96f, 0.56f, bz), 0.24f, 0.44f, 3.4f, darkChassis, metalness = 0.8f, roughness = 0.35f)
        addBox3D(outFaces, pose, Vec3(0.96f, 0.56f, bz), 0.24f, 0.44f, 3.4f, darkChassis, metalness = 0.8f, roughness = 0.35f)
        addBox3D(outFaces, pose, Vec3(0f, 0.66f, bz), 1.92f, 0.30f, 0.75f, darkChassis, metalness = 0.75f, roughness = 0.4f)

        // 2 Axles x 2 Wheels = 4 3D Cylinder Wheels per bogie (8 total)
        for (az in axleOffsetsZ) {
            val wz = bz + az
            // Axle shaft
            addBox3D(outFaces, pose, Vec3(0f, 0.54f, wz), 1.68f, 0.16f, 0.16f, darkChassis, metalness = 0.85f, roughness = 0.3f)
            // Left & Right 3D Cylinder Wheels
            addWheelCylinder3D(outFaces, pose, Vec3(-0.78f, 0.54f, wz), radius = 0.52f, width = 0.18f, wheelRotRad = wheelRotationRad, steelColor = wheelSteel, darkColor = darkChassis)
            addWheelCylinder3D(outFaces, pose, Vec3(0.78f, 0.54f, wz), radius = 0.52f, width = 0.18f, wheelRotRad = wheelRotationRad, steelColor = wheelSteel, darkColor = darkChassis)
        }
    }

    // 3. Belly Fuel Tank & Main Underframe Chassis Deck
    addBox3D(outFaces, pose, Vec3(0f, 0.70f, 0f), 2.62f, 0.82f, 5.2f, darkChassis, metalness = 0.78f, roughness = 0.36f)
    addBox3D(outFaces, pose, Vec3(0f, 1.18f, 0f), 3.16f, 0.44f, 16.4f, darkChassis, metalness = 0.78f, roughness = 0.32f)

    // Front & Rear Pilots (Snowplows) + Heavy Knuckle Couplers
    addBox3D(outFaces, pose, Vec3(0f, 0.68f, 8.15f), 2.94f, 0.64f, 0.52f, darkChassis, metalness = 0.82f, roughness = 0.3f)
    addBox3D(outFaces, pose, Vec3(0f, 0.68f, -8.15f), 2.94f, 0.64f, 0.52f, darkChassis, metalness = 0.82f, roughness = 0.3f)
    addBox3D(outFaces, pose, Vec3(0f, 0.74f, 8.55f), 0.48f, 0.34f, 0.75f, darkChassis, metalness = 0.85f, roughness = 0.28f)
    addBox3D(outFaces, pose, Vec3(0f, 0.74f, -8.55f), 0.48f, 0.34f, 0.75f, darkChassis, metalness = 0.85f, roughness = 0.28f)

    // 4. Long Hood (Main Diesel Engine Compartment) + Blue Heritage Stripe + Flared Radiator Section
    addBox3D(outFaces, pose, Vec3(0f, 2.58f, -2.05f), 2.50f, 2.36f, 9.8f, yellowBody, metalness = 0.56f, roughness = 0.25f)
    addBox3D(outFaces, pose, Vec3(0f, 2.26f, -2.05f), 2.55f, 0.52f, 9.86f, blueStripe, metalness = 0.60f, roughness = 0.28f, depthBias = -0.08f)
    addBox3D(outFaces, pose, Vec3(0f, 3.46f, -4.55f), 2.78f, 0.54f, 4.3f, yellowBody, metalness = 0.56f, roughness = 0.25f, depthBias = -0.06f)

    // 5. Sloped Silver Roof + 3D Cylindrical Cooling Fans + Exhaust Stack
    addBox3D(outFaces, pose, Vec3(0f, 3.86f, -2.05f), 2.34f, 0.24f, 9.72f, silverRoof, metalness = 0.85f, roughness = 0.22f)
    for (i in 0 until 3) {
        addVerticalCylinder3D(
            outFaces = outFaces,
            pose = pose,
            localCenter = Vec3(0f, 4.04f, -5.0f + i * 1.45f),
            radiusTop = 0.56f,
            radiusBottom = 0.58f,
            height = 0.20f,
            segments = 8,
            color = darkChassis,
            metalness = 0.82f,
            roughness = 0.28f,
            depthBias = -0.1f
        )
    }
    addVerticalCylinder3D(
        outFaces = outFaces,
        pose = pose,
        localCenter = Vec3(0f, 4.12f, -0.45f),
        radiusTop = 0.22f,
        radiusBottom = 0.26f,
        height = 0.46f,
        segments = 8,
        color = darkChassis,
        metalness = 0.85f,
        roughness = 0.3f,
        depthBias = -0.1f
    )

    // 6. Elevated Cab Section + Sloped Cab Roof + Darker Reflective Windows
    addBox3D(outFaces, pose, Vec3(0f, 2.72f, 4.15f), 3.02f, 2.62f, 3.42f, yellowBody, metalness = 0.58f, roughness = 0.24f)
    addBox3D(outFaces, pose, Vec3(0f, 2.26f, 4.15f), 3.06f, 0.52f, 3.46f, blueStripe, metalness = 0.60f, roughness = 0.28f, depthBias = -0.08f)
    addBox3D(outFaces, pose, Vec3(0f, 4.12f, 4.15f), 2.88f, 0.26f, 3.48f, silverRoof, metalness = 0.86f, roughness = 0.20f)

    // Front Windshields, Rear Cab Windows & Side Cab Windows
    addBox3D(outFaces, pose, Vec3(-0.74f, 3.34f, 5.84f), 1.12f, 0.78f, 0.14f, darkWindow, metalness = 0.95f, roughness = 0.08f, depthBias = -0.15f)
    addBox3D(outFaces, pose, Vec3(0.74f, 3.34f, 5.84f), 1.12f, 0.78f, 0.14f, darkWindow, metalness = 0.95f, roughness = 0.08f, depthBias = -0.15f)
    addBox3D(outFaces, pose, Vec3(-0.74f, 3.34f, 2.46f), 1.05f, 0.72f, 0.14f, darkWindow, metalness = 0.95f, roughness = 0.08f, depthBias = -0.15f)
    addBox3D(outFaces, pose, Vec3(0.74f, 3.34f, 2.46f), 1.05f, 0.72f, 0.14f, darkWindow, metalness = 0.95f, roughness = 0.08f, depthBias = -0.15f)
    addBox3D(outFaces, pose, Vec3(0f, 3.34f, 4.15f), 3.08f, 0.80f, 1.95f, darkWindow, metalness = 0.95f, roughness = 0.08f, depthBias = -0.15f)

    // Roof Air Horn Cluster
    addBox3D(outFaces, pose, Vec3(0f, 4.34f, 4.75f), 0.36f, 0.22f, 0.62f, handrailGold, metalness = 0.85f, roughness = 0.2f, depthBias = -0.12f)

    // 7. Short Front Nose Hood + Walkways Safety Handrails
    addBox3D(outFaces, pose, Vec3(0f, 2.26f, 6.84f), 2.62f, 1.72f, 2.22f, yellowBody, metalness = 0.56f, roughness = 0.25f)
    addBox3D(outFaces, pose, Vec3(-1.46f, 2.16f, -2.0f), 0.08f, 0.08f, 10.2f, handrailGold, metalness = 0.5f, roughness = 0.3f)
    addBox3D(outFaces, pose, Vec3(1.46f, 2.16f, -2.0f), 0.08f, 0.08f, 10.2f, handrailGold, metalness = 0.5f, roughness = 0.3f)

    // 8. Front & Rear Headlights + Ditch Lights
    val lampColor = if (headlightState == HeadlightState.OFF) Color(0xFF475569) else Color(0xFFFFF7AD)
    val isLampOn = headlightState != HeadlightState.OFF
    addBox3D(outFaces, pose, Vec3(0f, 2.78f, 7.96f), 0.58f, 0.30f, 0.16f, lampColor, emissive = isLampOn, depthBias = -0.2f)
    addBox3D(outFaces, pose, Vec3(0f, 3.38f, -6.98f), 0.54f, 0.28f, 0.16f, lampColor, emissive = isLampOn, depthBias = -0.2f)
    addBox3D(outFaces, pose, Vec3(-0.96f, 1.42f, 8.12f), 0.26f, 0.26f, 0.16f, lampColor, emissive = isLampOn, depthBias = -0.2f)
    addBox3D(outFaces, pose, Vec3(0.96f, 1.42f, 8.12f), 0.26f, 0.26f, 0.16f, lampColor, emissive = isLampOn, depthBias = -0.2f)
}

private fun buildFreightWagon3D(
    outFaces: MutableList<Face3D>,
    pose: Pose3D,
    wheelRotationRad: Float,
    carIndex: Int,
    sunDir: Vec3
) {
    val frameDark = Color(0xFF1E293B)
    val wheelSteel = Color(0xFF94A3B8)
    val bodyPalette = arrayOf(
        Color(0xFF0284C7), // Industrial Cyan-Blue
        Color(0xFFD97706), // BNSF Amber-Orange
        Color(0xFFDC2626), // Oxide Boxcar Red
        Color(0xFF16A34A)  // Forest Hopper Green
    )
    val bodyColor = bodyPalette[carIndex % bodyPalette.size]

    addCastShadow3D(outFaces, pose, Vec3(0f, 1.8f, 0f), 2.95f, 3.0f, 13.4f, pose.pos.y + 0.04f, sunDir)

    // Underframe & Bogies with 8 Rotating Wheels
    addBox3D(outFaces, pose, Vec3(0f, 1.08f, 0f), 2.92f, 0.38f, 13.4f, frameDark, metalness = 0.75f, roughness = 0.38f)
    for (bz in floatArrayOf(4.1f, -4.1f)) {
        addBox3D(outFaces, pose, Vec3(0f, 0.56f, bz), 1.98f, 0.36f, 2.2f, frameDark, metalness = 0.78f, roughness = 0.35f)
        for (az in floatArrayOf(-0.72f, 0.72f)) {
            addWheelCylinder3D(outFaces, pose, Vec3(-0.76f, 0.50f, bz + az), 0.48f, 0.16f, wheelRotationRad, wheelSteel, frameDark)
            addWheelCylinder3D(outFaces, pose, Vec3(0.76f, 0.50f, bz + az), 0.48f, 0.16f, wheelRotationRad, wheelSteel, frameDark)
        }
    }

    if (carIndex % 2 == 0) {
        // 3D Boxcar / Intermodal Container
        addBox3D(outFaces, pose, Vec3(0f, 2.48f, 0f), 2.82f, 2.45f, 12.2f, bodyColor, metalness = 0.45f, roughness = 0.36f)
        addBox3D(outFaces, pose, Vec3(0f, 3.74f, 0f), 2.68f, 0.14f, 12.1f, Color(0xFFCBD5E1), metalness = 0.8f, roughness = 0.25f)
    } else {
        // 3D Cylindrical Tank Car / Hopper
        addBox3D(outFaces, pose, Vec3(0f, 2.32f, 0f), 2.46f, 2.10f, 11.6f, bodyColor, metalness = 0.58f, roughness = 0.28f)
        addVerticalCylinder3D(outFaces, pose, Vec3(0f, 3.48f, 0f), 0.48f, 0.54f, 0.32f, 8, frameDark, metalness = 0.8f, roughness = 0.3f)
    }
}

// ============================================================================
// MAIN FULL-SCREEN 3D VIEWPORT COMPOSABLE (100% NATIVE & BLACK-SCREEN IMMUNE)
// ============================================================================

@Composable
fun SimViewport3D(
    positionMeters: Double,
    speedMps: Double,
    gradientPercent: Double,
    curveDeg: Double,
    throttleNotch: Int,
    reverser: Int,
    autoBrakePercent: Float,
    indBrakePercent: Float,
    brakePipePsi: Float,
    sandActive: Boolean,
    headlightState: HeadlightState,
    wipersActive: Boolean,
    wiperPhaseRad: Float,
    wheelRotationRad: Float,
    timeOfDay: TimeOfDayMode,
    weather: WeatherMode,
    qualityPreset: QualityPreset,
    cameraMode: CameraViewMode,
    locoIndex: Int,
    freightCarIndex: Int,
    freightCarCount: Int,
    nextSignalAspect: SignalAspect,
    distanceToNextSignal: Double,
    distanceToNextStation: Double,
    particles: Array<PooledParticle>,
    reducedMotion: Boolean,
    customLiveryColor: Color?,
    isSurveyorMode: Boolean,
    surveyorItems: List<SurveyorItemEntity>,
    onSurveyorGridTap: (Float, Float) -> Unit,
    onWebViewReady: (WebView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Interactive 3D Orbit Camera angles (drag anywhere on the 3D viewport to look around!)
    var orbitYawRad by remember { mutableFloatStateOf(0.58f) }
    var orbitPitchRad by remember { mutableFloatStateOf(0.32f) }

    // Dusk / Day / Dawn / Night Lighting Palette
    val isDusk = timeOfDay == TimeOfDayMode.DUSK || timeOfDay == TimeOfDayMode.DAWN
    val isNight = timeOfDay == TimeOfDayMode.NIGHT
    val skyTopColor = when {
        isNight -> Color(0xFF070D1D)
        isDusk -> Color(0xFF173168)
        else -> Color(0xFF1D4ED8)
    }
    val skyMidColor = when {
        isNight -> Color(0xFF132347)
        isDusk -> Color(0xFF6988B8)
        else -> Color(0xFF60A5FA)
    }
    val skyHorizonColor = when {
        isNight -> Color(0xFF1E293B)
        isDusk -> Color(0xFFF59556) // Warm Dusk Orange Gradient
        else -> Color(0xFFDBEAFE)
    }
    val sunColor = when {
        isNight -> Color(0xFF93C5FD)
        isDusk -> Color(0xFFFFE4B5)
        else -> Color(0xFFFFFFFF)
    }
    val skyAmbient = when {
        isNight -> Color(0xFF334155)
        isDusk -> Color(0xFFFFEBD6)
        else -> Color(0xFFE0F2FE)
    }
    val groundAmbient = Color(0xFF2A4726)
    val sunDir = Vec3(-0.55f, 0.68f, -0.48f).normalized()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(skyTopColor, skyMidColor, skyHorizonColor)
                )
            )
            .pointerInput(isSurveyorMode) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    orbitYawRad = (orbitYawRad - dragAmount.x * 0.0065f)
                    orbitPitchRad = (orbitPitchRad + dragAmount.y * 0.005f).coerceIn(0.08f, 1.15f)
                }
            }
            .pointerInput(isSurveyorMode) {
                detectTapGestures { tapOffset ->
                    if (isSurveyorMode) {
                        onSurveyorGridTap(tapOffset.x, tapOffset.y)
                    }
                }
            }
            .testTag("sim_viewport_3d")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 1f || h <= 1f) return@Canvas

            // 1. Draw Full-Screen Sky Dome Gradient & Glowing 3D Sun Disc
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to skyTopColor,
                    0.36f to skyMidColor,
                    0.68f to skyHorizonColor,
                    1.0f to Color(0xFF2D5E26)
                ),
                size = size
            )

            // 2. Compute Locomotive Pose on the 3D Spline
            val locoPose = evalTrackPose(positionMeters)

            // 3. Configure 3D PerspectiveCamera (FOV 65°) for Chase, Cab, Orbit, Trackside, or Fly-By
            val camera: PerspectiveCamera3D = when (cameraMode) {
                CameraViewMode.CAB_VIEW -> {
                    val cabEye = locoPose.toWorld(Vec3(0f, 3.38f, 4.55f))
                    val cabLook = locoPose.toWorld(Vec3(0f, 2.35f, 42.0f))
                    PerspectiveCamera3D(pos = cabEye, target = cabLook, fovDeg = 65f, width = w, height = h)
                }
                CameraViewMode.FREE_ORBIT -> {
                    val dist = 24.5f
                    val ox = sin(orbitYawRad) * cos(orbitPitchRad) * dist
                    val oy = sin(orbitPitchRad) * dist + 2.5f
                    val oz = -cos(orbitYawRad) * cos(orbitPitchRad) * dist
                    val camPos = locoPose.pos + Vec3(ox, max(2.2f, oy), oz)
                    val camTarget = locoPose.pos + Vec3(0f, 2.4f, 0f)
                    PerspectiveCamera3D(pos = camPos, target = camTarget, fovDeg = 65f, width = w, height = h)
                }
                CameraViewMode.TRACKSIDE_CAM -> {
                    val anchorZ = (kotlin.math.floor(positionMeters / 90.0) * 90.0) + 45.0
                    val anchorPose = evalTrackPose(anchorZ)
                    val camPos = anchorPose.toWorld(Vec3(12.5f, 3.8f, 0f))
                    val camTarget = locoPose.pos + Vec3(0f, 2.3f, 0f)
                    PerspectiveCamera3D(pos = camPos, target = camTarget, fovDeg = 60f, width = w, height = h)
                }
                CameraViewMode.STATION_FLYBY -> {
                    val camPos = locoPose.toWorld(Vec3(26.0f, 16.5f, 22.0f))
                    val camTarget = locoPose.toWorld(Vec3(0f, 2.0f, -4.0f))
                    PerspectiveCamera3D(pos = camPos, target = camTarget, fovDeg = 65f, width = w, height = h)
                }
                else -> {
                    // Default Chase 3D Camera: Positioned behind (-17.5m), elevated (+6.4m), and slightly offset
                    // so the 3D side profile, 8 rotating wheels, roof details, and curving 3D track are all visible!
                    val yawOffset = orbitYawRad - 0.58f
                    val lateralOffset = 4.2f + sin(yawOffset) * 5.0f
                    val camPos = locoPose.toWorld(Vec3(lateralOffset, 6.4f, -17.5f))
                    val camTarget = locoPose.toWorld(Vec3(0f, 2.15f, 18.0f))
                    PerspectiveCamera3D(pos = camPos, target = camTarget, fovDeg = 65f, width = w, height = h)
                }
            }

            // Project Sun Disc in the 3D sky
            val sunWorldPos = camera.pos + (sunDir * 520f)
            val sunCam = camera.worldToCamera(sunWorldPos)
            if (sunCam.z > 5f) {
                val sunScreen = camera.projectCameraPoint(sunCam)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            sunColor.copy(alpha = 0.85f),
                            skyHorizonColor.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = sunScreen,
                        radius = min(w, h) * 0.22f
                    ),
                    radius = min(w, h) * 0.22f,
                    center = sunScreen
                )
                drawCircle(
                    color = sunColor.copy(alpha = 0.95f),
                    radius = min(w, h) * 0.038f,
                    center = sunScreen
                )
            }

            // Collect all 3D Faces in the scene for Perspective Projection & Painter's Depth Sort
            val worldFaces = ArrayList<Face3D>(2400)

            // =================================================================
            // 4. 3D TERRAIN MESH (PLANEGEOMETRY WITH VERTEX DISPLACEMENT)
            // =================================================================
            val zStart = (kotlin.math.floor((positionMeters - 36.0) / 10.0) * 10.0).toFloat()
            val zSteps = 22
            val zStepSize = 10f
            val xOffsets = floatArrayOf(-150f, -96f, -54f, -26f, -11f, -4.5f, 4.5f, 11f, 26f, 54f, 96f, 150f)

            val grassDark = Color(0xFF285422)
            val grassMid = Color(0xFF3B742E)
            val grassBright = Color(0xFF528E3D)
            val embankmentBrown = Color(0xFF574E42)

            for (iz in 0 until zSteps) {
                val z0 = zStart + iz * zStepSize
                val z1 = z0 + zStepSize
                val tc0 = evalTrackCenter(z0.toDouble()).x
                val tc1 = evalTrackCenter(z1.toDouble()).x

                for (ix in 0 until xOffsets.size - 1) {
                    val x00 = tc0 + xOffsets[ix]
                    val x10 = tc0 + xOffsets[ix + 1]
                    val x11 = tc1 + xOffsets[ix + 1]
                    val x01 = tc1 + xOffsets[ix]

                    val y00 = evalHillHeight(x00, z0)
                    val y10 = evalHillHeight(x10, z0)
                    val y11 = evalHillHeight(x11, z1)
                    val y01 = evalHillHeight(x01, z1)

                    val v00 = Vec3(x00, y00, z0)
                    val v10 = Vec3(x10, y10, z0)
                    val v11 = Vec3(x11, y11, z1)
                    val v01 = Vec3(x01, y01, z1)

                    val normal = (v01 - v00).cross(v10 - v00).normalized().let {
                        if (it.y < 0f) it * -1f else it
                    }
                    val avgY = (y00 + y10 + y11 + y01) * 0.25f
                    val isCutSlope = ix == 4 || ix == 6
                    val isTrackSubgrade = ix == 5
                    val cellColor = when {
                        isTrackSubgrade -> embankmentBrown
                        isCutSlope -> Color(0xFF476335)
                        avgY > 14f -> grassBright
                        avgY > 4f -> grassMid
                        else -> grassDark
                    }

                    worldFaces.add(
                        Face3D(
                            v0 = v00,
                            v1 = v10,
                            v2 = v11,
                            v3 = v01,
                            normal = normal,
                            baseColor = cellColor,
                            metalness = 0.03f,
                            roughness = 0.90f,
                            depthBias = 4.0f
                        )
                    )
                }
            }

            // =================================================================
            // 5. 3D TRACK: GRAVEL BALLAST BED, 3D WOODEN SLEEPERS & 3D RAILS
            // =================================================================
            val trackZStart = kotlin.math.floor((positionMeters - 26.0) / 2.0) * 2.0
            val trackSegments = 88
            val segLen = 2.0
            val ballastTopColor = Color(0xFF645E56)
            val ballastSlopeColor = Color(0xFF4F4942)
            val sleeperWoodColor = Color(0xFF3E2B1D)
            val railSteelColor = Color(0xFFD8DEE9)
            val railWebColor = Color(0xFF475569)

            for (i in 0 until trackSegments) {
                val s0 = trackZStart + i * segLen
                val s1 = s0 + segLen
                val p0 = evalTrackPose(s0)
                val p1 = evalTrackPose(s1)

                // Gravel Ballast Top Crown & Sloped Side Shoulders
                val bLOut0 = p0.toWorld(Vec3(-2.65f, -0.38f, 0f))
                val bLTop0 = p0.toWorld(Vec3(-1.65f, -0.02f, 0f))
                val bRTop0 = p0.toWorld(Vec3(1.65f, -0.02f, 0f))
                val bROut0 = p0.toWorld(Vec3(2.65f, -0.38f, 0f))

                val bLOut1 = p1.toWorld(Vec3(-2.65f, -0.38f, 0f))
                val bLTop1 = p1.toWorld(Vec3(-1.65f, -0.02f, 0f))
                val bRTop1 = p1.toWorld(Vec3(1.65f, -0.02f, 0f))
                val bROut1 = p1.toWorld(Vec3(2.65f, -0.38f, 0f))

                worldFaces.add(
                    Face3D(bLTop0, bRTop0, bRTop1, bLTop1, p0.up, ballastTopColor, metalness = 0.05f, roughness = 0.94f, depthBias = 1.8f)
                )
                worldFaces.add(
                    Face3D(bLOut0, bLTop0, bLTop1, bLOut1, (p0.up + p0.right * -0.6f).normalized(), ballastSlopeColor, roughness = 0.95f, depthBias = 1.9f)
                )
                worldFaces.add(
                    Face3D(bRTop0, bROut0, bROut1, bRTop1, (p0.up + p0.right * 0.6f).normalized(), ballastSlopeColor, roughness = 0.95f, depthBias = 1.9f)
                )

                // 3D Wooden Sleepers (Ties) as 3D Boxes along the spline
                if (i < 56) {
                    val sm = evalTrackPose(s0 + 0.5)
                    addBox3D(
                        outFaces = worldFaces,
                        pose = sm,
                        localCenter = Vec3(0f, 0.04f, 0f),
                        sizeX = 2.56f,
                        sizeY = 0.14f,
                        sizeZ = 0.28f,
                        color = sleeperWoodColor,
                        metalness = 0.06f,
                        roughness = 0.88f,
                        depthBias = 1.1f
                    )
                }

                // Left & Right 3D Steel Rails (1.435m standard gauge, +/- 0.72m)
                for (rx in floatArrayOf(-0.72f, 0.72f)) {
                    // Rail Top Running Surface
                    val rTopL0 = p0.toWorld(Vec3(rx - 0.065f, 0.20f, 0f))
                    val rTopR0 = p0.toWorld(Vec3(rx + 0.065f, 0.20f, 0f))
                    val rTopR1 = p1.toWorld(Vec3(rx + 0.065f, 0.20f, 0f))
                    val rTopL1 = p1.toWorld(Vec3(rx - 0.065f, 0.20f, 0f))
                    worldFaces.add(
                        Face3D(rTopL0, rTopR0, rTopR1, rTopL1, p0.up, railSteelColor, metalness = 0.90f, roughness = 0.18f, depthBias = 0.6f)
                    )
                    // Rail Side Web
                    val rBot0 = p0.toWorld(Vec3(rx + 0.05f, 0.05f, 0f))
                    val rBot1 = p1.toWorld(Vec3(rx + 0.05f, 0.05f, 0f))
                    worldFaces.add(
                        Face3D(rBot0, rTopR0, rTopR1, rBot1, p0.right, railWebColor, metalness = 0.75f, roughness = 0.35f, depthBias = 0.7f)
                    )
                }
            }

            // =================================================================
            // 6. 3D TREES (CYLINDER TRUNK + 2 GREEN CONE TIERS + 3D SHADOWS)
            // =================================================================
            val treeStartBucket = (kotlin.math.floor((positionMeters - 28.0) / 14.0).toInt())
            val treeEndBucket = treeStartBucket + 16
            val trunkBrown = Color(0xFF5C3A21)
            val leavesDarkGreen = Color(0xFF1F5124)
            val leavesBrightGreen = Color(0xFF2B6B30)

            for (bucket in treeStartBucket..treeEndBucket) {
                val baseZ = bucket * 14f
                val trackCenter = evalTrackCenter(baseZ.toDouble())

                // Place 4 deterministic 3D trees per bucket (2 left of track, 2 right of track)
                for (slot in 0 until 4) {
                    val hash = ((bucket * 73856093) xor (slot * 19349663)) and 0x7FFFFFFF
                    val rand01 = (hash % 1000) / 1000f
                    val rand02 = ((hash / 1000) % 1000) / 1000f
                    val side = if (slot % 2 == 0) -1f else 1f
                    val lateralDist = (13.5f + (slot / 2) * 24f + rand01 * 16f) * side
                    val tx = trackCenter.x + lateralDist
                    val tz = baseZ + (rand02 - 0.5f) * 11f
                    val ty = evalHillHeight(tx, tz) - 0.15f
                    val treeScale = 0.85f + rand01 * 0.55f

                    val treeBase = Vec3(tx, ty, tz)
                    val treePose = Pose3D(
                        pos = treeBase,
                        right = Vec3(1f, 0f, 0f),
                        up = Vec3(0f, 1f, 0f),
                        forward = Vec3(0f, 0f, 1f)
                    )

                    // Real-time 3D Tree Shadow on the hillside
                    addCastShadow3D(
                        outFaces = worldFaces,
                        pose = treePose,
                        localCenter = Vec3(0f, 3.6f * treeScale, 0f),
                        sizeX = 4.2f * treeScale,
                        sizeY = 7.2f * treeScale,
                        sizeZ = 4.2f * treeScale,
                        groundY = ty + 0.05f,
                        sunDir = sunDir
                    )

                    // 3D Brown Cylinder Trunk
                    addVerticalCylinder3D(
                        outFaces = worldFaces,
                        pose = treePose,
                        localCenter = Vec3(0f, 1.5f * treeScale, 0f),
                        radiusTop = 0.28f * treeScale,
                        radiusBottom = 0.44f * treeScale,
                        height = 3.0f * treeScale,
                        segments = 6,
                        color = trunkBrown,
                        metalness = 0.03f,
                        roughness = 0.92f
                    )

                    // Lower & Upper 3D Green Cones (`ConeGeometry`)
                    addCone3D(
                        outFaces = worldFaces,
                        worldBaseCenter = treeBase + Vec3(0f, 2.3f * treeScale, 0f),
                        radius = 2.45f * treeScale,
                        height = 4.8f * treeScale,
                        segments = 6,
                        color = leavesDarkGreen
                    )
                    addCone3D(
                        outFaces = worldFaces,
                        worldBaseCenter = treeBase + Vec3(0f, 5.0f * treeScale, 0f),
                        radius = 1.80f * treeScale,
                        height = 4.1f * treeScale,
                        segments = 6,
                        color = leavesBrightGreen
                    )
                }
            }

            // 3D Trackside Signals along the route
            val nextSigZ = positionMeters + distanceToNextSignal.coerceIn(25.0, 220.0)
            val sigPose = evalTrackPose(nextSigZ)
            addBox3D(worldFaces, sigPose, Vec3(2.85f, 2.7f, 0f), 0.18f, 5.4f, 0.18f, Color(0xFF64748B), metalness = 0.8f, roughness = 0.3f)
            addBox3D(worldFaces, sigPose, Vec3(2.85f, 5.1f, -0.15f), 0.52f, 1.35f, 0.36f, Color(0xFF0F172A), metalness = 0.5f, roughness = 0.4f)
            val sigLampColor = when (nextSignalAspect) {
                SignalAspect.CLEAR_GREEN -> Color(0xFF22C55E)
                SignalAspect.APPROACH_YELLOW -> Color(0xFFFACC15)
                SignalAspect.STOP_RED -> Color(0xFFEF4444)
            }
            addBox3D(worldFaces, sigPose, Vec3(2.85f, 5.1f, -0.35f), 0.30f, 0.30f, 0.12f, sigLampColor, emissive = true, depthBias = -0.3f)

            // =================================================================
            // 7. DETAILED 3D LOCOMOTIVE & COUPLED 3D FREIGHT CONSIST
            // =================================================================
            if (cameraMode != CameraViewMode.CAB_VIEW) {
                buildDetailed3DLocomotive(
                    outFaces = worldFaces,
                    pose = locoPose,
                    wheelRotationRad = wheelRotationRad,
                    customLiveryColor = customLiveryColor,
                    headlightState = headlightState,
                    sunDir = sunDir
                )

                val visibleCars = min(freightCarCount.coerceAtLeast(2), 4)
                for (cIdx in 0 until visibleCars) {
                    val carDist = positionMeters - (cIdx + 1) * 15.6
                    val carPose = evalTrackPose(carDist)
                    buildFreightWagon3D(
                        outFaces = worldFaces,
                        pose = carPose,
                        wheelRotationRad = wheelRotationRad,
                        carIndex = cIdx + freightCarIndex,
                        sunDir = sunDir
                    )
                }
            } else {
                // In Cab 3D View, render the 3D short front hood out the windshield + 3D cab interior frame & levers
                addBox3D(worldFaces, locoPose, Vec3(0f, 2.22f, 6.84f), 2.60f, 1.68f, 2.22f, customLiveryColor ?: Color(0xFFF59E0B), metalness = 0.56f, roughness = 0.25f)
                addBox3D(worldFaces, locoPose, Vec3(-0.52f, 2.65f, 5.38f), 0.85f, 0.55f, 0.45f, Color(0xFF1E293B), metalness = 0.8f, roughness = 0.3f)
                val thrZ = 5.35f + (throttleNotch / 8f) * 0.22f
                val brkZ = 5.35f + (autoBrakePercent / 100f) * 0.22f
                addBox3D(worldFaces, locoPose, Vec3(-0.36f, 3.05f, thrZ), 0.07f, 0.34f, 0.07f, Color(0xFFF59E0B), metalness = 0.6f, roughness = 0.2f)
                addBox3D(worldFaces, locoPose, Vec3(-0.64f, 3.05f, brkZ), 0.07f, 0.34f, 0.07f, Color(0xFFEF4444), metalness = 0.6f, roughness = 0.2f)
            }

            // =================================================================
            // 8. PERSPECTIVE PROJECTION, FRUSTUM CLIPPING & Z-SORT RASTERIZER
            // =================================================================
            val projectedPolys = ArrayList<ProjectedPoly>(worldFaces.size)

            for (i in 0 until worldFaces.size) {
                val f = worldFaces[i]
                val c0 = camera.worldToCamera(f.v0)
                val c1 = camera.worldToCamera(f.v1)
                val c2 = camera.worldToCamera(f.v2)
                val c3 = f.v3?.let { camera.worldToCamera(it) }

                val minZ = if (c3 != null) min(min(c0.z, c1.z), min(c2.z, c3.z)) else min(c0.z, min(c1.z, c2.z))
                val maxZ = if (c3 != null) max(max(c0.z, c1.z), max(c2.z, c3.z)) else max(c0.z, max(c1.z, c2.z))
                if (maxZ < camera.near || minZ < 0.25f || minZ > camera.far) continue

                val p0 = camera.projectCameraPoint(c0)
                val p1 = camera.projectCameraPoint(c1)
                val p2 = camera.projectCameraPoint(c2)
                val p3 = c3?.let { camera.projectCameraPoint(it) }

                // Quick screen-bounds check
                val minX = if (p3 != null) min(min(p0.x, p1.x), min(p2.x, p3.x)) else min(p0.x, min(p1.x, p2.x))
                val maxX = if (p3 != null) max(max(p0.x, p1.x), max(p2.x, p3.x)) else max(p0.x, max(p1.x, p2.x))
                val minY = if (p3 != null) min(min(p0.y, p1.y), min(p2.y, p3.y)) else min(p0.y, min(p1.y, p2.y))
                val maxY = if (p3 != null) max(max(p0.y, p1.y), max(p2.y, p3.y)) else max(p0.y, max(p1.y, p2.y))
                if (maxX < -200f || minX > w + 200f || maxY < -200f || minY > h + 200f) continue

                val worldCenter = if (f.v3 != null) {
                    (f.v0 + f.v1 + f.v2 + f.v3) * 0.25f
                } else {
                    (f.v0 + f.v1 + f.v2) * (1f / 3f)
                }
                val distToCam = (worldCenter - camera.pos).length() + f.depthBias

                val shaded = shade3DFace(
                    normal = f.normal,
                    worldCenter = worldCenter,
                    baseColor = f.baseColor,
                    metalness = f.metalness,
                    roughness = f.roughness,
                    emissive = f.emissive,
                    isShadow = f.isShadow,
                    alpha = f.alpha,
                    camPos = camera.pos,
                    sunDir = sunDir,
                    sunColor = sunColor,
                    skyAmbient = skyAmbient,
                    groundAmbient = groundAmbient,
                    fogColor = skyHorizonColor
                )

                val strokeCol = if (!f.isShadow && !f.emissive && f.depthBias <= 0.5f) {
                    Color(0x22000000)
                } else {
                    null
                }

                if (p3 != null) {
                    projectedPolys.add(
                        ProjectedPoly(
                            pts = floatArrayOf(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y, p3.x, p3.y),
                            count = 4,
                            sortDepth = distToCam,
                            shadedColor = shaded,
                            strokeColor = strokeCol
                        )
                    )
                } else {
                    projectedPolys.add(
                        ProjectedPoly(
                            pts = floatArrayOf(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y),
                            count = 3,
                            sortDepth = distToCam,
                            shadedColor = shaded,
                            strokeColor = strokeCol
                        )
                    )
                }
            }

            // Back-to-front Painter's sort
            projectedPolys.sortByDescending { it.sortDepth }

            val polyPath = Path()
            for (i in 0 until projectedPolys.size) {
                val poly = projectedPolys[i]
                polyPath.reset()
                polyPath.moveTo(poly.pts[0], poly.pts[1])
                for (v in 1 until poly.count) {
                    polyPath.lineTo(poly.pts[v * 2], poly.pts[v * 2 + 1])
                }
                polyPath.close()
                drawPath(path = polyPath, color = poly.shadedColor, style = Fill)
                if (poly.strokeColor != null) {
                    drawPath(path = polyPath, color = poly.strokeColor, style = Stroke(width = 1.0f))
                }
            }

            // =================================================================
            // 9. VOLUMETRIC 3D HEADLIGHT CONE & 3D DIESEL EXHAUST SMOKE PLUMES
            // =================================================================
            if (headlightState != HeadlightState.OFF) {
                val lampOrigin = locoPose.toWorld(Vec3(0f, 2.75f, 8.1f))
                val beamLeft = locoPose.toWorld(Vec3(-4.2f, 0.2f, 36.0f))
                val beamRight = locoPose.toWorld(Vec3(4.2f, 0.2f, 36.0f))
                val cO = camera.worldToCamera(lampOrigin)
                val cL = camera.worldToCamera(beamLeft)
                val cR = camera.worldToCamera(beamRight)
                if (cO.z > 0.6f && cL.z > 0.6f && cR.z > 0.6f) {
                    val sO = camera.projectCameraPoint(cO)
                    val sL = camera.projectCameraPoint(cL)
                    val sR = camera.projectCameraPoint(cR)
                    polyPath.reset()
                    polyPath.moveTo(sO.x, sO.y)
                    polyPath.lineTo(sL.x, sL.y)
                    polyPath.lineTo(sR.x, sR.y)
                    polyPath.close()
                    val beamAlpha = if (headlightState == HeadlightState.BRIGHT) 0.22f else 0.11f
                    drawPath(
                        path = polyPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFF7AD).copy(alpha = beamAlpha),
                                Color(0xFFFFF7AD).copy(alpha = 0.02f)
                            )
                        )
                    )
                }
            }

            // Render 3D Diesel Exhaust Smoke Spheres rising from the exhaust stack
            if (cameraMode != CameraViewMode.CAB_VIEW) {
                val stackWorld = locoPose.toWorld(Vec3(0f, 4.35f, -0.45f))
                val puffCount = 9
                val animPhase = (wheelRotationRad * 0.35f) % 1f
                for (p in 0 until puffCount) {
                    val age = ((p.toFloat() + animPhase) / puffCount).coerceIn(0f, 1f)
                    val puffPos = stackWorld +
                        (locoPose.forward * (-age * (4.5f + abs(speedMps.toFloat()) * 0.35f))) +
                        Vec3(sin(age * 6f) * 0.35f, age * (3.2f + throttleNotch * 0.45f), 0f)
                    val camPt = camera.worldToCamera(puffPos)
                    if (camPt.z > 1.0f) {
                        val screenPt = camera.projectCameraPoint(camPt)
                        val worldRad = 0.42f + age * (1.25f + throttleNotch * 0.14f)
                        val screenRad = (worldRad * camera.focalLength / camPt.z).coerceIn(2f, 90f)
                        val puffAlpha = ((1f - age) * (0.22f + throttleNotch * 0.03f)).coerceIn(0f, 0.48f)
                        drawCircle(
                            color = Color(0xFFD8DEE9).copy(alpha = puffAlpha),
                            radius = screenRad,
                            center = screenPt
                        )
                    }
                }
            }

            // Optional Weather Precip (Rain / Fog in 3D perspective)
            if (weather == WeatherMode.LIGHT_RAIN || weather == WeatherMode.FOG) {
                drawWeatherOverlay(weather, wheelRotationRad)
            }
        }
    }
}

private fun DrawScope.drawWeatherOverlay(weather: WeatherMode, phase: Float) {
    val count = if (weather == WeatherMode.FOG) 40 else 70
    val w = size.width
    val h = size.height
    for (i in 0 until count) {
        val seed = (i * 9283711) and 0x7FFFFFFF
        val rx = ((seed % 1000) / 1000f * w + phase * 18f) % w
        val ry = (((seed / 1000) % 1000) / 1000f * h + phase * 95f) % h
        if (weather == WeatherMode.LIGHT_RAIN) {
            drawLine(
                color = Color(0x66BAE6FD),
                start = Offset(rx, ry),
                end = Offset(rx - 5f, ry + 22f),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )
        } else {
            drawCircle(
                color = Color(0x33E2E8F0),
                radius = 18.0f,
                center = Offset(rx, ry)
            )
        }
    }
}
