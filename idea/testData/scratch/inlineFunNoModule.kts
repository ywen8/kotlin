// This test checks that scratch with inline function from script dependencies compiles successfully
// inline function should be outside of project model (kotlin-reflect.jar isn't included in test project)

import kotlin.reflect.full.findAnnotation

annotation class Ann

@Ann class A

A::class.findAnnotation<Ann>() != null
