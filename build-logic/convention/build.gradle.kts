plugins {
    `kotlin-dsl`
}

group = "com.sankare.wifikill.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinLibrary") {
            id = "wifikill.kotlin.library"
            implementationClass = "com.sankare.wifikill.buildlogic.KotlinLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "wifikill.android.library"
            implementationClass = "com.sankare.wifikill.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "wifikill.android.compose"
            implementationClass = "com.sankare.wifikill.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "wifikill.android.feature"
            implementationClass = "com.sankare.wifikill.buildlogic.AndroidFeatureConventionPlugin"
        }
    }
}
