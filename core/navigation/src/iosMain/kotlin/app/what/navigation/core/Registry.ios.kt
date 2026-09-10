package app.what.navigation.core

import kotlin.reflect.KClass

actual fun <P : NavProvider, S : NavComponent<P>> instantiateScreen(screen: KClass<S>, provider: P): S {
    val factory = screenFactories[screen] ?: error("Screen factory not registered for ${screen.simpleName}")
    @Suppress("UNCHECKED_CAST")
    return factory(provider) as S
}
