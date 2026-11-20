plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("com.google.firebase.crashlytics") version "3.0.6" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false

}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}