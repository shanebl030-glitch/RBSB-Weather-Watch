// RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather

/**
 * The text shown on the About screen. Kept free of Android classes so it can be unit-tested
 * on the JVM. Stage 0 shell: there are no weather features yet.
 */
object AboutInfo {
    const val APP_TITLE = "RBSB-Weather Watch"
    const val CREDIT = "Created By Shane BlackbuRn"
    const val STAGE_NOTE = "Stage 0 shell: no weather yet"

    /** The lines of the About screen, top to bottom. */
    fun lines(versionName: String): List<String> =
        listOf(APP_TITLE, "Version $versionName", CREDIT, STAGE_NOTE)
}
