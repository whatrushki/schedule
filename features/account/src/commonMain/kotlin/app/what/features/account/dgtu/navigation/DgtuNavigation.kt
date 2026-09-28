package app.what.schedule.features.insts.dgtu.navigation

import app.what.navigation.core.NavProvider
import kotlinx.serialization.Serializable

@Serializable
internal object DGTUAuthProvider : NavProvider()

@Serializable
internal object DGTUMainProvider : NavProvider()

@Serializable
internal object DGTUMailProvider : NavProvider()

@Serializable
internal object DGTUCertificateProvider : NavProvider()

@Serializable
internal object DGTUEventProvider : NavProvider()

@Serializable
internal object DGTUZachBookProvider : NavProvider()

@Serializable
internal data class DGTUMailDetailProvider(val threadId: Int, val messageId: Int) : NavProvider()

