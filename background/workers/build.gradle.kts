plugins {
    id("wifikill.android.library")
}

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    implementation(project(":data:repository"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
