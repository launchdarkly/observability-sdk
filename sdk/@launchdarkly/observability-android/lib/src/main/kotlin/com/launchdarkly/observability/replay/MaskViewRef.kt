package com.launchdarkly.observability.replay

import kotlin.reflect.KClass

sealed interface MaskViewRef {
    val clazz: Class<*>

    data class FromClass(
        override val clazz: Class<*>
    ) : MaskViewRef

    data class FromKClass(
        val kclass: KClass<*>
    ) : MaskViewRef {
        override val clazz: Class<*> = kclass.java
    }

    data class FromName(
        val fullClassName: String
    ) : MaskViewRef {
        override val clazz: Class<*> =
            try {
                Class.forName(fullClassName)
            } catch (e: ClassNotFoundException) {
                throw IllegalArgumentException(
                    "PrivacyProfile.maskViews contains an invalid class name: '$fullClassName'. " +
                        "Provide a fully-qualified Android View class name (e.g. 'android.widget.TextView').",
                    e
                )
            }
    }
}

fun view(clazz: Class<*>): MaskViewRef = MaskViewRef.FromClass(clazz)
fun view(kclass: KClass<*>): MaskViewRef = MaskViewRef.FromKClass(kclass)
fun view(name: String): MaskViewRef = MaskViewRef.FromName(name)
