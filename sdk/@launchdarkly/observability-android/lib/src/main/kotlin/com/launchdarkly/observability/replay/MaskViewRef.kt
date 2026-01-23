package com.launchdarkly.observability.replay

import kotlin.reflect.KClass

sealed interface MaskViewRef {
    val clazz: Class<*>

    data class FromClass(
        override val clazz: Class<*>
    ) : MaskViewRef

    data class FromKClass(
        val kClass: KClass<*>
    ) : MaskViewRef {
        override val clazz: Class<*> = kClass.java
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
fun view(kClass: KClass<*>): MaskViewRef = MaskViewRef.FromKClass(kClass)
fun view(name: String): MaskViewRef = MaskViewRef.FromName(name)
