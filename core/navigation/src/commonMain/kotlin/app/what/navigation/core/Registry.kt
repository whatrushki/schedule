package app.what.navigation.core

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlin.reflect.KClass

typealias Registry = NavGraphBuilder.() -> Unit

expect fun <P : NavProvider, S : NavComponent<P>> instantiateScreen(screen: KClass<S>, provider: P): S

val screenFactories = mutableMapOf<KClass<*>, (Any) -> Any>()

fun <P : NavProvider, S : NavComponent<P>> registerScreenFactory(screen: KClass<S>, factory: (P) -> S) {
    screenFactories[screen] = { provider -> factory(provider as P) }
}

inline fun <reified P : NavProvider, S : NavComponent<P>> NavGraphBuilder.register(screen: KClass<S>) {
    composable<P> {
        val provider = it.toRoute<P>()
        val s = remember(provider) { instantiateScreen(screen, provider) }
        s.content(Modifier)
    }
}

inline fun <reified P : NavProvider, S : NavComponent<P>> NavGraphBuilder.register(
    screen: KClass<S>,
    noinline factory: (P) -> S
) {
    registerScreenFactory(screen, factory)
    composable<P> {
        val provider = it.toRoute<P>()
        val s = remember(provider) { factory(provider) }
        s.content(Modifier)
    }
}