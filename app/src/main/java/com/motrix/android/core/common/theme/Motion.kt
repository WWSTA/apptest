package com.motrix.android.core.common.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MD3 Emphasized Easing curves
val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

// MD3 Standard Easing curves
val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
val StandardAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
val StandardDecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

// Animation durations (ms)
object MotionDuration {
    const val pageTransitionEnter = 500
    const val pageTransitionExit = 350
    const val containerTransformEnter = 500
    const val containerTransformExit = 350
    const val modalEnter = 350
    const val modalExit = 250
    const val fabExpand = 350
    const val fabCollapse = 200
    const val cardAppear = 300
    const val cardDisappear = 200
    const val listItem = 250
    const val listItemExit = 200
    const val microInteraction = 100
    const val tabIndicator = 300
}

// Spring specs
object MotionSpring {
    val StandardSpatial = SpringSpec<Float>(
        dampingRatio = 0.9f,
        stiffness = 700f
    )
    val StandardSpatialFast = SpringSpec<Float>(
        dampingRatio = 0.9f,
        stiffness = 1400f
    )
    val StandardSpatialSlow = SpringSpec<Float>(
        dampingRatio = 0.9f,
        stiffness = 300f
    )
    val ExpressiveSpatial = SpringSpec<Float>(
        dampingRatio = 0.8f,
        stiffness = 380f
    )
    val ExpressiveSpatialFast = SpringSpec<Float>(
        dampingRatio = 0.6f,
        stiffness = 800f
    )
    val ExpressiveSpatialSlow = SpringSpec<Float>(
        dampingRatio = 0.8f,
        stiffness = 200f
    )
    val ExpressiveEffect = SpringSpec<Float>(
        dampingRatio = 0.7f,
        stiffness = 400f
    )
    val ExpressiveEffectFast = SpringSpec<Float>(
        dampingRatio = 0.6f,
        stiffness = 800f
    )
    val ExpressiveEffectSlow = SpringSpec<Float>(
        dampingRatio = 0.8f,
        stiffness = 200f
    )
}

// Tween specs for common animations
object MotionTween {
    fun pageTransitionEnter() = TweenSpec<Float>(
        durationMs = MotionDuration.pageTransitionEnter,
        easing = EmphasizedDecelerateEasing
    )

    fun pageTransitionExit() = TweenSpec<Float>(
        durationMs = MotionDuration.pageTransitionExit,
        easing = EmphasizedAccelerateEasing
    )

    fun modalEnter() = TweenSpec<Float>(
        durationMs = MotionDuration.modalEnter,
        easing = StandardDecelerateEasing
    )

    fun modalExit() = TweenSpec<Float>(
        durationMs = MotionDuration.modalExit,
        easing = StandardAccelerateEasing
    )

    fun fabExpand() = TweenSpec<Float>(
        durationMs = MotionDuration.fabExpand,
        easing = EmphasizedDecelerateEasing
    )

    fun cardAppear() = TweenSpec<Float>(
        durationMs = MotionDuration.cardAppear,
        easing = StandardDecelerateEasing
    )

    fun listItem() = TweenSpec<Float>(
        durationMs = MotionDuration.listItem,
        easing = StandardDecelerateEasing
    )

    fun microInteraction() = TweenSpec<Float>(
        durationMs = MotionDuration.microInteraction,
        easing = StandardEasing
    )

    fun tabIndicator() = TweenSpec<Dp>(
        durationMs = MotionDuration.tabIndicator,
        easing = EmphasizedEasing
    )
}
