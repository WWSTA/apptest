package com.motrix.android

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.motrix.android.core.common.theme.EmphasizedAccelerateEasing
import com.motrix.android.core.common.theme.EmphasizedDecelerateEasing
import com.motrix.android.core.common.theme.MotionDuration
import com.motrix.android.feature.newtask.NewTaskRoute
import com.motrix.android.feature.settings.SettingsRoute
import com.motrix.android.feature.taskdetail.TaskDetailRoute
import com.motrix.android.feature.tasklist.TaskListRoute

internal object Routes {
    const val TASK_LIST = "taskList"
    const val NEW_TASK = "newTask"
    const val TASK_DETAIL = "taskDetail/{gid}"
    const val SETTINGS = "settings"

    fun taskDetail(gid: String) = "taskDetail/$gid"
}

@Composable
fun MotrixNavHost(
    pendingMagnetUri: String?,
    onMagnetUriConsumed: () -> Unit,
) {
    val navController = rememberNavController()

    LaunchedEffect(pendingMagnetUri) {
        if (pendingMagnetUri != null) {
            navController.navigate(Routes.NEW_TASK)
            onMagnetUriConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.TASK_LIST,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(
                    durationMillis = MotionDuration.pageTransitionEnter,
                    easing = EmphasizedDecelerateEasing,
                ),
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = MotionDuration.pageTransitionEnter,
                    easing = EmphasizedDecelerateEasing,
                ),
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(
                    durationMillis = MotionDuration.pageTransitionExit,
                    easing = EmphasizedAccelerateEasing,
                ),
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = MotionDuration.pageTransitionExit,
                    easing = EmphasizedAccelerateEasing,
                ),
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(
                    durationMillis = MotionDuration.pageTransitionEnter,
                    easing = EmphasizedDecelerateEasing,
                ),
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = MotionDuration.pageTransitionEnter,
                    easing = EmphasizedDecelerateEasing,
                ),
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(
                    durationMillis = MotionDuration.pageTransitionExit,
                    easing = EmphasizedAccelerateEasing,
                ),
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = MotionDuration.pageTransitionExit,
                    easing = EmphasizedAccelerateEasing,
                ),
            )
        },
    ) {
        composable(Routes.TASK_LIST) {
            TaskListRoute(
                onNavigateToNewTask = { navController.navigate(Routes.NEW_TASK) },
                onNavigateToTaskDetail = { gid -> navController.navigate(Routes.taskDetail(gid)) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.NEW_TASK) {
            NewTaskRoute(
                initialUrl = pendingMagnetUri,
                onDismiss = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.TASK_DETAIL,
            arguments = listOf(
                navArgument("gid") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val gid = backStackEntry.arguments?.getString("gid").orEmpty()
            TaskDetailRoute(
                gid = gid,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
