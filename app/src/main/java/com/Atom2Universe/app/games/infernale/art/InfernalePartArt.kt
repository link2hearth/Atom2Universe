package com.Atom2Universe.app.games.infernale.art

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Sprites procéduraux 64 × 64, agrandis sans lissage. Une instance par vue (thread UI).
 * Aucun mouvement automatique : le jeu fournit angle, course, niveau et activation.
 * Le tampon est réutilisé ; le renderer ne modifie jamais la simulation.
 */
class InfernalePartArt {
    data class State(
        val angle: Float = 0f, // radians, rotation interne ; rotation du corps gérée par l'appelant
        val travel: Float = 0f, // course normalisée 0..1
        val level: Float = 0.5f,
        val active: Boolean = false,
        val phase: Float = 0f // cycles, défilement des courroies / fluides
    )

    enum class Shape { BEAM, PANEL, FRAME, BOLT, WHEEL, GEAR, SHAFT, BELT, SPRING,
        PISTON, LINKAGE, PIPE, TANK, VALVE, MOTOR, BALL, BARREL, BOX, BATTERY, BUTTON, GAUGE, CIRCUIT }

    private val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    private val raster = InfernalePixels()
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val target = RectF()

    companion object {
        const val BACKGROUND: Int = 0xFFFFF6E7.toInt()

        /** Explicit mapping: unlisted future parts remain detectable, never silently substituted. */
        val shapes: Map<String, Shape> = buildMap {
            fun group(shape: Shape, prefix: String, names: String) {
                names.split(' ').forEach { put("$prefix.$it", shape) }
            }
            group(Shape.BEAM, "structure", "beam")
            group(Shape.PANEL, "structure", "plate block ballast")
            group(Shape.FRAME, "structure", "frame ground_anchor")
            group(Shape.BOLT, "structure", "spacer bolt weld breakable_joint rivet")
            group(Shape.SHAFT, "rotary", "solid_shaft hollow_shaft axle crankshaft")
            group(Shape.WHEEL, "rotary", "bearing bushing wheel flywheel drum cam eccentric winch")
            group(Shape.LINKAGE, "rotary", "crank")
            group(Shape.GEAR, "transmission", "spur_gear internal_ring_gear helical_gear worm_wheel sprocket ratchet")
            group(Shape.WHEEL, "transmission", "planet_carrier pulley clutch torque_limiter brake freewheel")
            group(Shape.SHAFT, "transmission", "rack worm")
            group(Shape.BELT, "transmission", "belt chain")
            group(Shape.MOTOR, "transmission", "gearbox differential")
            group(Shape.LINKAGE, "transmission", "universal_joint")
            group(Shape.SHAFT, "linear", "guide lead_screw")
            group(Shape.PANEL, "linear", "carriage")
            group(Shape.SPRING, "linear", "spring")
            group(Shape.PISTON, "linear", "damper gas_spring")
            group(Shape.PIPE, "linear", "rope steel_cable")
            group(Shape.LINKAGE, "linear", "lever toggle_linkage scissor_lift")
            group(Shape.BELT, "linear", "conveyor")
            group(Shape.TANK, "pneumatic", "chamber receiver")
            group(Shape.PIPE, "pneumatic", "pipe hose manifold quick_exhaust venturi")
            group(Shape.VALVE, "pneumatic", "shutoff_valve check_valve regulator relief_valve")
            group(Shape.MOTOR, "pneumatic", "compressor vacuum_pump air_motor")
            group(Shape.PISTON, "pneumatic", "single_cylinder double_cylinder")
            group(Shape.BARREL, "pneumatic", "nozzle launcher")
            group(Shape.TANK, "hydraulic", "reservoir accumulator abrasive_feeder")
            group(Shape.PIPE, "hydraulic", "rigid_pipe hose manifold filter")
            group(Shape.VALVE, "hydraulic", "directional_valve flow_valve check_valve relief_valve")
            group(Shape.MOTOR, "hydraulic", "pump hand_pump motor")
            group(Shape.PISTON, "hydraulic", "cylinder ram")
            group(Shape.BARREL, "hydraulic", "nozzle waterjet_head")
            group(Shape.FRAME, "hydraulic", "press_frame")
            group(Shape.BALL, "projectile", "sphere slug potato sabot")
            group(Shape.BARREL, "projectile", "barrel breech muzzle_brake")
            group(Shape.BOX, "projectile", "catch_box")
            group(Shape.MOTOR, "energy", "rotary_motor electric_motor generator combustion_engine steam_engine turbine")
            group(Shape.PISTON, "energy", "linear_motor")
            group(Shape.BATTERY, "energy", "battery capacitor")
            group(Shape.LINKAGE, "energy", "human_crank")
            group(Shape.BUTTON, "control", "trigger switch")
            group(Shape.GAUGE, "control", "timer")
            group(Shape.CIRCUIT, "control", "logic_gate pid")
            group(Shape.LINKAGE, "control", "governor")
            group(Shape.GAUGE, "sensor", "pressure tachometer encoder force position")
            group(Shape.CIRCUIT, "sensor", "zone")
            group(Shape.BUTTON, "sensor", "limit_switch")
            group(Shape.WHEEL, "safety", "rupture_disk")
            group(Shape.BATTERY, "safety", "fuse")
            group(Shape.BUTTON, "safety", "emergency_stop")
        }
    }

    /** Bounds in caller coordinates, Y down. Aspect ratio preserved; transparent background. */
    fun draw(canvas: Canvas, id: String, bounds: RectF, state: State = State(), alpha: Int = 255) {
        if (id !in shapes) return
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        val pixels = raster.render(id, state.angle, state.travel, state.level, state.active, state.phase)
        bitmap.setPixels(pixels, 0, 64, 0, 0, 64, 64)
        val size = minOf(bounds.width(), bounds.height())
        target.set(bounds.centerX() - size / 2, bounds.centerY() - size / 2, bounds.centerX() + size / 2, bounds.centerY() + size / 2)
        paint.alpha = alpha.coerceIn(0, 255)
        canvas.drawBitmap(bitmap, null, target, paint)
        paint.alpha = 255
    }

}


