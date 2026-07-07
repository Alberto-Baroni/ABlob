package com.example.ablob

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Splash : NavKey
@Serializable data object Login : NavKey
@Serializable data class Browser(val prefix: String = "") : NavKey
@Serializable data object Settings : NavKey
