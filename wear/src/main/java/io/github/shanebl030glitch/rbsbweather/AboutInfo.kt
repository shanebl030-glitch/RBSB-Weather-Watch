// RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather

/**
 * The text shown on the About screen. Kept free of Android classes so it can be unit-tested
 * on the JVM.
 */
object AboutInfo {
    const val APP_TITLE = "RBSB-Weather Watch"
    const val CREDIT = "Created By Shane BlackbuRn"
    const val STAGE_NOTE = "Stage 2 in progress: the sky shows sample data until the phone link (Stage 3)"
    /** NASA credit for the Moon textures (approved Stage 2 decision 5), as the phone app words it. */
    const val MOON_CREDIT = "Moon imagery: NASA's Scientific Visualization Studio, CGI Moon Kit (LRO LROC WAC and LOLA data)"

    /** The lines of the About screen, top to bottom. */
    fun lines(versionName: String): List<String> =
        listOf(APP_TITLE, "Version $versionName", CREDIT, STAGE_NOTE, MOON_CREDIT)
}
