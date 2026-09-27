package com.example.sim

import androidx.compose.ui.graphics.Color

/**
 * Frozen simulation configuration constants (`CONFIG`) so tuning never requires hunting through code.
 */
object TrainSimConfig {
    const val FIXED_DT: Double = 1.0 / 60.0
    const val MAX_ACCUMULATOR: Double = 0.25 // Caps accumulator to prevent spiral of death
    const val GRAVITY: Double = 9.80665
    const val MAX_THROTTLE_NOTCH: Int = 8
    const val BRAKE_PIPE_MAX_PSI: Float = 90.0f
    const val BRAKE_PIPE_MIN_PSI: Float = 64.0f
    const val EMERGENCY_BRAKE_PSI: Float = 0.0f

    // Adhesion & resistance constants (Davis equation: A + B*v + C*v^2)
    const val DAVIS_A_LOCO: Double = 6.5
    const val DAVIS_A_CAR: Double = 1.8
    const val DAVIS_B: Double = 0.045
    const val DAVIS_C: Double = 0.0012
    const val DRY_ADHESION_COEFF: Double = 0.33
    const val SLIP_PENALTY_FACTOR: Double = 0.42
    const val COUPLER_SLACK_MAX_M: Double = 0.18

    // Rendering & Memory budgets (4 GB RAM / 60 FPS Mandate)
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

enum class SignalAspect(
    val label: String,
    val shapeName: String,
    val speedLimitMps: Double,
    val colorHex: Long
) {
    CLEAR_GREEN("CLEAR", "SQUARE", 45.0, 0xFF10B981),
    APPROACH_YELLOW("APPROACH", "DIAMOND", 20.0, 0xFFFACC15),
    STOP_RED("STOP", "CIRCLE", 0.0, 0xFFEF4444)
}

data class LocomotiveSpec(
    val id: String,
    val name: String,
    val massTons: Double,
    val maxTractiveEffortKn: Double,
    val maxPowerKw: Double,
    val maxSpeedKmH: Double,
    val triangleCount: Int,
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
        title = "Free Roam",
        subtitle = "Pure sandbox across the 6 km Alpine Loop. No speed or schedule penalties.",
        defaultLocoIndex = 0,
        freightCarCount = 5,
        steepGradeFactor = 0.6,
        maxSpeedLimitKmH = 120.0,
        stations = listOf(
            StationStop("Timberline Yard", 600.0, 60),
            StationStop("Oakridge Junction", 1800.0, 140),
            StationStop("Summit Pass", 3200.0, 240)
        )
    ),
    PASSENGER_RUN(
        title = "Passenger Run",
        subtitle = "5 timed station stops with ±10s tolerance scoring. Smooth braking required.",
        defaultLocoIndex = 3,
        freightCarCount = 6,
        steepGradeFactor = 0.8,
        maxSpeedLimitKmH = 140.0,
        stations = listOf(
            StationStop("Central Terminal", 550.0, 45),
            StationStop("Riverside Halt", 1300.0, 95),
            StationStop("Beacon Valley", 2150.0, 150),
            StationStop("Northridge Parkway", 3050.0, 210),
            StationStop("Grand Summit Depot", 4000.0, 275)
        )
    ),
    FREIGHT_HAUL(
        title = "Freight Haul",
        subtitle = "1,480-ton consist over a 2.4% ruling grade. Do not stall or exceed 65 km/h.",
        defaultLocoIndex = 1,
        freightCarCount = 12,
        steepGradeFactor = 2.2,
        maxSpeedLimitKmH = 65.0,
        stations = listOf(
            StationStop("Coal Loading Siding", 900.0, 90),
            StationStop("Iron Gorge Crest", 2400.0, 220),
            StationStop("Steelworks Exchange", 3900.0, 340)
        )
    )
}

object HardcodedAssetLibrary {
    // 5 Locomotives (all <= 800 triangles)
    val locomotives: List<LocomotiveSpec> = listOf(
        LocomotiveSpec("loco_sd40", "EMD SD40-2 Road Switcher", 167.0, 365.0, 2240.0, 105.0, 740, Color(0xFFF59E0B), Color(0xFF1E293B)),
        LocomotiveSpec("loco_es44", "GE ES44AC Heavy Haul", 195.0, 530.0, 3280.0, 112.0, 780, Color(0xFFEA580C), Color(0xFF0F172A)),
        LocomotiveSpec("loco_class66", "EMD Class 66 Euro Freight", 129.0, 409.0, 2460.0, 120.0, 710, Color(0xFF10B981), Color(0xFFFACC15)),
        LocomotiveSpec("loco_vectron", "Alpine Express Electric", 85.0, 320.0, 6400.0, 160.0, 680, Color(0xFF38BDF8), Color(0xFFF8FAFC)),
        LocomotiveSpec("loco_mikado", "2-8-2 Heritage Steam", 142.0, 285.0, 1950.0, 90.0, 790, Color(0xFF334155), Color(0xFFEF4444))
    )

    // 10 Freight / Rolling Stock Cars (all <= 400 triangles)
    val freightCars: List<FreightCarSpec> = listOf(
        FreightCarSpec("car_box", "50ft Hi-Cube Boxcar", 28.0, 92.0, 16.5f, 280, Color(0xFF9A3412)),
        FreightCarSpec("car_hopper", "100-Ton Coal Hopper", 26.0, 118.0, 15.8f, 320, Color(0xFF1E293B)),
        FreightCarSpec("car_tank", "DOT-117 Hazmat Tank Car", 31.0, 108.0, 17.2f, 360, Color(0xFF334155)),
        FreightCarSpec("car_flat", "60ft Bulkhead Flatcar", 24.0, 84.0, 19.0f, 220, Color(0xFF78350F)),
        FreightCarSpec("car_intermodal", "Double-Stack Well Car", 29.0, 96.0, 20.5f, 340, Color(0xFF0284C7)),
        FreightCarSpec("car_gondola", "Mill Gondola Scrap Car", 27.0, 104.0, 16.0f, 260, Color(0xFF475569)),
        FreightCarSpec("car_autorack", "Tri-Level Autorack", 41.0, 86.0, 27.0f, 310, Color(0xFFD97706)),
        FreightCarSpec("car_reefer", "Cryogenic Reefer Car", 34.0, 94.0, 18.2f, 290, Color(0xFFE2E8F0)),
        FreightCarSpec("car_coach", "Bi-Level Commuter Coach", 48.0, 62.0, 25.0f, 380, Color(0xFF0EA5E9)),
        FreightCarSpec("car_caboose", "Wide-Vision Steel Caboose", 24.0, 26.0, 11.5f, 350, Color(0xFFDC2626))
    )

    // 20 Scenery Items (all <= 150 triangles)
    val sceneryItems: List<ScenerySpec> = listOf(
        ScenerySpec("scn_pine", "Alpine Pine Tree", "Flora", 48, 16, Color(0xFF15803D), 14f),
        ScenerySpec("scn_spruce", "Blue Spruce Cluster", "Flora", 64, 20, Color(0xFF166534), 16f),
        ScenerySpec("scn_oak", "Deciduous Oak", "Flora", 72, 24, Color(0xFF4D7C0F), 12f),
        ScenerySpec("scn_boulder", "Granite Outcrop", "Geology", 44, 14, Color(0xFF64748B), 5f),
        ScenerySpec("scn_cliff", "Slate Cliff Slab", "Geology", 80, 28, Color(0xFF475569), 22f),
        ScenerySpec("scn_depot", "Timber Passenger Depot", "Structures", 136, 42, Color(0xFFB45309), 9f),
        ScenerySpec("scn_tower", "Interlocking Signal Tower", "Structures", 118, 36, Color(0xFF9A3412), 13f),
        ScenerySpec("scn_silo", "Twin Concrete Grain Silo", "Industry", 142, 48, Color(0xFFCBD5E1), 24f),
        ScenerySpec("scn_water", "Trackside Water Tank", "Structures", 110, 32, Color(0xFF78350F), 11f),
        ScenerySpec("scn_coaltipple", "Mine Coal Tipple", "Industry", 148, 52, Color(0xFF334155), 20f),
        ScenerySpec("scn_catenary", "Steel Catenary Mast", "Trackside", 36, 12, Color(0xFF94A3B8), 8.5f),
        ScenerySpec("scn_signal_gantry", "Cantilever Signal Bridge", "Trackside", 92, 28, Color(0xFF64748B), 9.5f),
        ScenerySpec("scn_crossing", "Grade Crossing Gate", "Trackside", 56, 18, Color(0xFFEF4444), 4.5f),
        ScenerySpec("scn_milepost", "Concrete Milepost Marker", "Trackside", 24, 8, Color(0xFFF8FAFC), 1.8f),
        ScenerySpec("scn_relaybox", "Wayside Relay Cabinet", "Trackside", 28, 12, Color(0xFF94A3B8), 2.4f),
        ScenerySpec("scn_warehouse", "Corrugated Freight Shed", "Industry", 96, 32, Color(0xFF475569), 10f),
        ScenerySpec("scn_substation", "Traction Transformer Yard", "Industry", 128, 40, Color(0xFF0284C7), 7.5f),
        ScenerySpec("scn_bridge_truss", "Warren Steel Bridge Span", "Structures", 144, 48, Color(0xFF334155), 12f),
        ScenerySpec("scn_fence", "Wooden Snow Fence", "Trackside", 32, 10, Color(0xFFA16207), 2.2f),
        ScenerySpec("scn_lamp", "Yard Floodlight Tower", "Trackside", 52, 16, Color(0xFFFACC15), 15f)
    )
}
