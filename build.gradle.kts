plugins {
    alias(libs.plugins.android).apply(false)
    alias(libs.plugins.kotlinSerialization).apply(false)
    alias(libs.plugins.compose).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.hilt).apply(false)
}

buildscript {
    configurations.classpath {
        resolutionStrategy.force("com.squareup:javapoet:1.13.0")
    }
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
