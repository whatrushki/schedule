package app.what.navigation.core

import kotlin.reflect.KClass

actual fun <P : NavProvider, S : NavComponent<P>> instantiateScreen(screen: KClass<S>, provider: P): S {
    val factory = screenFactories[screen]
    if (factory != null) {
        @Suppress("UNCHECKED_CAST")
        return factory(provider) as S
    }
    val singleParamCtor = screen.constructors.firstOrNull { it.parameters.size == 1 }
    if (singleParamCtor != null) {
        @Suppress("UNCHECKED_CAST")
        return singleParamCtor.call(provider)
    }
    @Suppress("UNCHECKED_CAST")
    return screen.constructors.first().call(provider)
}
