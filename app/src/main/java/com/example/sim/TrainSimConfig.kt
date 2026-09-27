package com.example.sim

import androidx.compose.ui.graphics.Color

/**
 * Frozen simulation configuration constants (`CONFIG`) for Trainz: A New Era 3D Simulator.
 */
object TrainSimConfig {
    const val FIXED_DT: Double = 1.0 / 60.0
    const val MAX_ACCUMULATOR: Double = 0.25
    const val GRAVITY: Double = 9.80665
    const val MAX_THROTTLE_NOTCH: Int = 8
    const val BRAKE_PIPE_MAX_PSI: Float = 90.0f
    const val BRAKE_PIPE_MIN_PSI: Float = 64.0f
    const val EMERGENCY_BRAKE_PSI: Float = 0.0f

    // Davis equation & adhesion constants
    const val DAVIS_A_LOCO: Double = 6.5
    const val DAVIS_A_CAR: Double = 1.8
    const val DAVIS_B: Double = 0.045
    const val DAVIS_C: Double = 0.0012
    const val DRY_ADHESION_COEFF: Double = 0.33
    const val WET_ADHESION_COEFF: Double = 0.21
    const val SAND_ADHESION_MULTIPLIER: Double = 1.35
    const val SLIP_PENALTY_FACTOR: Double = 0.42
    const val COUPLER_SLACK_MAX_M: Double = 0.18

    // Rendering & Memory budgets
    const val MAX_DRAW_CALLS_BUDGET: Int = 80
    const val MAX_TRIANGLES_BUDGET: Int = 100_000
    const val MAX_RAM_MB_BUDGET: Int = 350
    const val HEAP_SOFT_RESET_MB: Int = 300
    const val TEXTURE_MAX_RES: Int = 256
    const val FOG_DISTANCE_M: Float = 400.0f
    const val LOD_NEAR_M: Float = 80.0f
    const val LOD_FAR_M: Float = 200.0f

    // Pool sizes (Pre-allocated at boot; zero `new` in update loop)
    const val PARTICLE_POOL_SIZE: Int = 64
    const val TIE_POOL_SIZE: Int = 120
    const val UNDO_STACK_MAX: Int = 20
    const val SAVE_SLOT_MAX_BYTES: Int = 200 * 1024
    const val SAVE_SCHEMA_VERSION: Int = 1
}

enum class TimeOfDayMode(
    val label: String,
    val skyTopHex: Long,
    val skyHorizonHex: Long,
    val groundHex: Long,
    val nightGlowIntensity: Float
) {
    DAWN("Dawn", 0xFF1E1B4B, 0xFFF97316, 0xFF1F2937, 0.35f),
    DAY("Day", 0xFF0284C7, 0xFFBAE6FD, 0xFF1E3A29, 0.0f),
    DUSK("Dusk", 0xFF0B1325, 0xFFD97706, 0xFF1A2521, 0.65f),
    NIGHT("Night", 0xFF030712, 0xFF0F172A, 0xFF090D14, 1.0f)
}

enum class WeatherMode(val label: String) {
    CLEAR("Clear"),
    OVERCAST("Overcast"),
    LIGHT_RAIN("Light Rain"),
    FOG("Heavy Fog")
}

enum class HeadlightState(val label: String) {
    OFF("OFF"),
    DIM("DIM"),
    BRIGHT("BRIGHT")
}

enum class QualityPreset(
    val label: String,
    val drawDistanceScale: Float,
    val shadowsEnabled: Boolean,
    val bloomEnabled: Boolean
) {
    LOW("Low", 0.65f, false, false),
    MEDIUM("Medium", 0.85f, true, false),
    HIGH("High", 1.0f, true, true),
    ULTRA("Ultra", 1.25f, true, true)
}

enum class SignalAspect(
    val label: String,
    val shapeName: String,
    val speedLimitMps: Double,
    val colorHex: Long,
    val semaphoreAngleDeg: Float
) {
    CLEAR_GREEN("CLEAR", "SQUARE", 45.0, 0xFF10B981, -45f),
    APPROACH_YELLOW("CAUTION", "DIAMOND", 20.0, 0xFFFACC15, -22f),
    STOP_RED("STOP", "CIRCLE", 0.0, 0xFFEF4444, 0f)
}

data class LocomotiveSpec(
    val id: String,
    val name: String,
    val tractionType: String, // "Diesel-Electric", "Electric", "Steam"
    val roadNumber: String,
    val massTons: Double,
    val maxTractiveEffortKn: Double,
    val maxPowerKw: Double,
    val maxSpeedKmH: Double,
    val triangleCount: Int, // 2,000 - 4,000+ high-detail locomotive meshes
    val primaryColor: Color,
    val accentColor: Color
)

data class FreightCarSpec(
    val id: String,
    val name: String,
    val emptyMassTons: Double,
    val loadedMassTons: Double,
    val lengthMeters: Float,
    val triangleCount: Int,
    val bodyColor: Color
)

data class ScenerySpec(
    val id: String,
    val name: String,
    val category: String,
    val triangleCountLod0: Int,
    val triangleCountLod1: Int,
    val baseColor: Color,
    val heightMeters: Float
)

data class StationStop(
    val name: String,
    val distanceMeters: Double,
    val targetArrivalSec: Int,
    val dwellSec: Int = 10
)

enum class ScenarioId(
    val title: String,
    val subtitle: String,
    val defaultLocoIndex: Int,
    val freightCarCount: Int,
    val steepGradeFactor: Double,
    val maxSpeedLimitKmH: Double,
    val stations: List<StationStop>
) {
    FREE_ROAM(
        title = "Free Drive",
        subtitle = "Pick any locomotive & consist across the 8 km Alpine & Valley route.",
        defaultLocoIndex = 0,
        freightCarCount = 5,
        steepGradeFactor = 0.7,
        maxSpeedLimitKmH = 120.0,
        stations = listOf(
            StationStop("Timberline Depot", 600.0, 60),
            StationStop("Oakridge Town", 1650.0, 140),
            StationStop("Riverbend Viaduct", 2800.0, 220),
            StationStop("Grand Summit Terminal", 4200.0, 320)
        )
    ),
    PASSENGER_RUN(
        title = "Passenger Service",
        subtitle = "6 timed station stops with punctuality & stopping accuracy scoring.",
        defaultLocoIndex = 3,
        freightCarCount = 6,
        steepGradeFactor = 0.85,
        maxSpeedLimitKmH = 140.0,
        stations = listOf(
            StationStop("Central Terminal", 550.0, 45),
            StationStop("Riverside Halt", 1300.0, 95),
            StationStop("Beacon Valley", 2150.0, 150),
            StationStop("Northridge Parkway", 3050.0, 210),
            StationStop("Emerald Lake Station", 3900.0, 265),
            StationStop("Grand Summit Depot", 4800.0, 325)
        )
    ),
    FREIGHT_HAUL(
        title = "Freight Run",
        subtitle = "1,480-ton consist over a 2.2% ruling grade. Manage wheel slip, sand, and brake fade.",
        defaultLocoIndex = 1,
        freightCarCount = 12,
        steepGradeFactor = 2.2,
        maxSpeedLimitKmH = 65.0,
        stations = listOf(
            StationStop("Coal Loading Siding", 900.0, 90),
            StationStop("Iron Gorge Crest", 2400.0, 220),
            StationStop("Steelworks Exchange", 3900.0, 340)
        )
    ),
    TUTORIAL(
        title = "Tutorial",
        subtitle = "Step-by-step interactive cab coaching: Reverser, Brakes, Horn, Throttle, and Station Stop.",
        defaultLocoIndex = 0,
        freightCarCount = 4,
        steepGradeFactor = 0.3,
        maxSpeedLimitKmH = 80.0,
        stations = listOf(
            StationStop("Timberline Training Platform", 480.0, 75),
            StationStop("Oakridge Valley Depot", 1400.0, 160)
        )
    )
}

object HardcodedAssetLibrary {
    // 5 Detailed Locomotives (2,950 - 4,120 triangles per locomotive)
    val locomotives: List<LocomotiveSpec> = listOf(
        LocomotiveSpec("loco_sd40", "EMD SD40-2 Road Switcher", "Diesel-Electric", "IR-4028", 167.0, 365.0, 2240.0, 105.0, 3420, Color(0xFFF59E0B), Color(0xFF1E293B)),
        LocomotiveSpec("loco_es44", "GE ES44AC Heavy Haul", "Diesel-Electric", "IR-8814", 195.0, 530.0, 3280.0, 112.0, 3860, Color(0xFFEA580C), Color(0xFF0F172A)),
        LocomotiveSpec("loco_class66", "EMD Class 66 Euro Freight", "Diesel-Electric", "IR-6609", 129.0, 409.0, 2460.0, 120.0, 3180, Color(0xFF10B981), Color(0xFFFACC15)),
        LocomotiveSpec("loco_vectron", "Alpine Vectron Electric", "Electric", "IR-193", 85.0, 320.0, 6400.0, 160.0, 2950, Color(0xFF38BDF8), Color(0xFFF8FAFC)),
        LocomotiveSpec("loco_mikado", "2-8-2 Mikado Heritage Steam", "Steam", "IR-282", 142.0, 285.0, 1950.0, 90.0, 4120, Color(0xFF334155), Color(0xFFEF4444))
    )

    // 10 Detailed Rolling Stock Cars (Passenger Coaches + Freight Wagons)
    val freightCars: List<FreightCarSpec> = listOf(
        FreightCarSpec("car_coach", "Bi-Level Panorama Coach", 48.0, 62.0, 25.0f, 680, Color(0xFF0EA5E9)),
        FreightCarSpec("car_box", "50ft Hi-Cube Boxcar", 28.0, 92.0, 16.5f, 520, Color(0xFF9A3412)),
        FreightCarSpec("car_flat", "60ft Timber Bulkhead Flatbed", 24.0, 84.0, 19.0f, 460, Color(0xFF78350F)),
        FreightCarSpec("car_tank", "DOT-117 Pressurized Tanker", 31.0, 108.0, 17.2f, 640, Color(0xFF334155)),
        FreightCarSpec("car_hopper", "100-Ton Ribbed Coal Hopper", 26.0, 118.0, 15.8f, 580, Color(0xFF1E293B)),
        FreightCarSpec("car_intermodal", "Double-Stack Container Well", 29.0, 96.0, 20.5f, 610, Color(0xFF0284C7)),
        FreightCarSpec("car_gondola", "Heavy Mill Steel Gondola", 27.0, 104.0, 16.0f, 490, Color(0xFF475569)),
        FreightCarSpec("car_autorack", "Tri-Level Enclosed Autorack", 41.0, 86.0, 27.0f, 540, Color(0xFFD97706)),
        FreightCarSpec("car_reefer", "Cryogenic Mechanical Reefer", 34.0, 94.0, 18.2f, 510, Color(0xFFE2E8F0)),
        FreightCarSpec("car_caboose", "Wide-Vision Cupola Caboose", 24.0, 26.0, 11.5f, 620, Color(0xFFDC2626))
    )

    // 20 Route & Scenery Items
    val sceneryItems: List<ScenerySpec> = listOf(
        ScenerySpec("scn_pine", "Alpine Pine Tree", "Flora", 120, 32, Color(0xFF15803D), 14f),
        ScenerySpec("scn_spruce", "Blue Spruce Cluster", "Flora", 140, 36, Color(0xFF166534), 16f),
        ScenerySpec("scn_oak", "Broadleaf Valley Oak", "Flora", 148, 40, Color(0xFF4D7C0F), 12f),
        ScenerySpec("scn_boulder", "Granite Cliff Outcrop", "Geology", 96, 28, Color(0xFF64748B), 5f),
        ScenerySpec("scn_cliff", "Layered Slate Ridge", "Geology", 136, 42, Color(0xFF475569), 22f),
        ScenerySpec("scn_depot", "Brick Station Building & Canopy", "Stations", 340, 96, Color(0xFFB45309), 9f),
        ScenerySpec("scn_footbridge", "Station Steel Footbridge", "Stations", 280, 84, Color(0xFF475569), 8.5f),
        ScenerySpec("scn_tower", "Interlocking Signal Box", "Structures", 220, 64, Color(0xFF9A3412), 13f),
        ScenerySpec("scn_silo", "Twin Concrete Grain Elevator", "Industry", 310, 88, Color(0xFFCBD5E1), 24f),
        ScenerySpec("scn_coaltipple", "Valley Coal Loader Tipple", "Industry", 350, 98, Color(0xFF334155), 20f),
        ScenerySpec("scn_catenary", "Overhead Catenary Portal", "Trackside", 110, 32, Color(0xFF94A3B8), 8.5f),
        ScenerySpec("scn_signal_gantry", "Semaphore & Light Signal Mast", "Trackside", 160, 48, Color(0xFF64748B), 9.5f),
        ScenerySpec("scn_crossing", "Automated Level Crossing Gate", "Trackside", 124, 36, Color(0xFFEF4444), 4.5f),
        ScenerySpec("scn_townhouse", "Half-Timbered Town House", "Town", 240, 72, Color(0xFFD97706), 11f),
        ScenerySpec("scn_church", "Village Stone Spire", "Town", 290, 82, Color(0xFF94A3B8), 19f),
        ScenerySpec("scn_warehouse", "Freight Distribution Shed", "Industry", 190, 56, Color(0xFF475569), 10f),
        ScenerySpec("scn_substation", "Traction Feeder Substation", "Industry", 230, 68, Color(0xFF0284C7), 7.5f),
        ScenerySpec("scn_bridge_truss", "Warren Steel Viaduct Span", "Structures", 320, 92, Color(0xFF334155), 12f),
        ScenerySpec("scn_fence", "Lineside Timber Fence", "Trackside", 64, 20, Color(0xFFA16207), 2.2f),
        ScenerySpec("scn_lamp", "Platform Heritage Lamp Post", "Stations", 88, 24, Color(0xFFFACC15), 6.5f)
    )
}
