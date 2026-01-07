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
        val fqcn: String
    ) : MaskViewRef {
        override val clazz: Class<*> = Class.forName(fqcn)
    }
}

fun view(clazz: Class<*>): MaskViewRef = MaskViewRef.FromClass(clazz)
fun view(kclass: KClass<*>): MaskViewRef = MaskViewRef.FromKClass(kclass)
fun view(name: String): MaskViewRef = MaskViewRef.FromName(name)
