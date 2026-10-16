// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.ben.manes.versions)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.gms.gradle) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.firebase.appdistribution) apply false
    alias(libs.plugins.play.publisher) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}