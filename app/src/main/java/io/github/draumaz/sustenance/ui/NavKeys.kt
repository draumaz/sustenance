package io.github.draumaz.sustenance.ui

import kotlinx.serialization.Serializable

@Serializable
sealed interface NavKey {
    @Serializable
    data object Today : NavKey
    
    @Serializable
    data object Insights : NavKey
    
    @Serializable
    data class Settings(val scrollTo: String? = null) : NavKey
    
    @Serializable
    data class Detail(val metricKey: String) : NavKey
}
