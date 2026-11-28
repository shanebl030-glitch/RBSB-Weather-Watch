// RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather

/**
 * The text shown in the About section. Kept free of Android classes so it can be unit-tested
 * on the JVM.
 */
object AboutInfo {
    const val APP_TITLE = "RBSB-Weather Watch"
    const val CREDIT = "Created By Shane BlackbuRn"
    const val SOURCE_NOTE = "Weather from RBSB-Weather for Android on your phone"
    /** NASA credit for the Moon textures (approved Stage 2 decision 5), as the phone app words it. */
    const val MOON_CREDIT = "Moon imagery: NASA's Scientific Visualization Studio, CGI Moon Kit (LRO LROC WAC and LOLA data)"

    /** The fixed lines of the About section, top to bottom. */
    fun lines(versionName: String): List<String> =
        listOf(APP_TITLE, "Version $versionName", CREDIT, SOURCE_NOTE, MOON_CREDIT)

    /**
     * The fixed lines plus the data's status: the place, the provider, when it was fetched and the
     * phone link. Status lines are inserted after the version, before the credits.
     */
    fun lines(versionName: String, status: List<String>): List<String> {
        val fixed = lines(versionName)
        return fixed.take(2) + status + fixed.drop(2)
    }
}
