plugins {
    id("wifikill.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    implementation(project(":data:repository"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.hilt.compiler)
}
