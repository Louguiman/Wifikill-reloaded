package com.sankare.wifikill.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        val modulePath = path.trim(':').replace(':', '.').replace('-', '_')
        val namespace = if (modulePath.isEmpty()) {
            "com.sankare.wifikill"
        } else {
            "com.sankare.wifikill.$modulePath"
        }

        extensions.configure<LibraryExtension> {
            compileSdk = 35
            defaultConfig {
                minSdk = 29
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            this.namespace = namespace
        }

        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(17)
        }
    }
}
