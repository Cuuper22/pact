// Top-level build file. Plugin versions come from the version catalog; the
// `apply false` keeps them off the root classpath so the :app module applies them.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
