/*
 * PUI theme icon patch - ports the PUIThemeTwitterIcon.apk runtime overlay
 * (RRO targeting com.twitter.android) into a Morphe resource patch.
 *
 * Effective overrides on X 12.25.2-prod.01:
 *  - in-app vector icons (ic_vector_*) replaced with classic Twitter-styled set
 *  - launcher foreground (ic_launcher_foreground) replaced with the classic bird
 *  - adaptive app icons ic_app_icon_2..14 -> gray background + bird
 *  - launcher background color ic_launcher_background -> #FF2A99E1
 *  - app name (app_name / APP_NAME) -> "Twitter"
 */
package app.crimera.patches.newx.misc.puitheme

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import org.w3c.dom.Element
import org.w3c.dom.NodeList

// Mirrors app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X (internal there)
private val COMPATIBILITY_NEW_X =
    Compatibility(
        packageName = "com.twitter.android",
        name = "NewX",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0,
        targets =
            listOf(
                AppTarget("12.25.0-alpha.01"),
                AppTarget("12.25.0-prod.01"),
                AppTarget("12.25.2-prod.01"),
                AppTarget("12.26.0-alpha.01", isExperimental = true),
                AppTarget("12.26.0-alpha.02", isExperimental = true),
                AppTarget("12.26.0-alpha.03", isExperimental = true),
                AppTarget("12.27.0-alpha.01", isExperimental = true),
            ),
    )

private val DRAWABLE = arrayOf(
    "ic_launcher_foreground.xml",
    "ic_vector_accessibility_alt.xml",
    "ic_vector_accessibility_circle.xml",
    "ic_vector_bar_chart.xml",
    "ic_vector_bar_chart_bold.xml",
    "ic_vector_bookmark_plus_stroke.xml",
    "ic_vector_bookmark_stroke.xml",
    "ic_vector_browser_globe.xml",
    "ic_vector_camera_shortcut.xml",
    "ic_vector_camera_stroke.xml",
    "ic_vector_chat.xml",
    "ic_vector_chat_round_bubble_stroke.xml",
    "ic_vector_chat_stroke.xml",
    "ic_vector_close.xml",
    "ic_vector_communities.xml",
    "ic_vector_communities_close_stroke.xml",
    "ic_vector_communities_plus_stroke.xml",
    "ic_vector_communities_stroke.xml",
    "ic_vector_compose.xml",
    "ic_vector_compose_lists.xml",
    "ic_vector_device_notification.xml",
    "ic_vector_eye.xml",
    "ic_vector_filter.xml",
    "ic_vector_filter_fill.xml",
    "ic_vector_flask.xml",
    "ic_vector_flask_stroke.xml",
    "ic_vector_follow_arrows.xml",
    "ic_vector_globe.xml",
    "ic_vector_globe_stroke.xml",
    "ic_vector_grok_text.xml",
    "ic_vector_heart_broken_stroke.xml",
    "ic_vector_heartline.xml",
    "ic_vector_help_circle.xml",
    "ic_vector_help_circle_fill.xml",
    "ic_vector_home.xml",
    "ic_vector_home_stroke.xml",
    "ic_vector_incoming_fill.xml",
    "ic_vector_incoming_stroke.xml",
    "ic_vector_jobs_stroke.xml",
    "ic_vector_layers.xml",
    "ic_vector_layers_stroke.xml",
    "ic_vector_lightbulb_stroke_off.xml",
    "ic_vector_lightbulb_stroke_on.xml",
    "ic_vector_lightning.xml",
    "ic_vector_lightning_stroke.xml",
    "ic_vector_link.xml",
    "ic_vector_lists.xml",
    "ic_vector_lists_stroke.xml",
    "ic_vector_location_stroke.xml",
    "ic_vector_lock.xml",
    "ic_vector_lock_stroke.xml",
    "ic_vector_messages.xml",
    "ic_vector_messages_stroke.xml",
    "ic_vector_money.xml",
    "ic_vector_money_stroke.xml",
    "ic_vector_more_circle.xml",
    "ic_vector_notifications.xml",
    "ic_vector_notifications_off.xml",
    "ic_vector_notifications_stroke.xml",
    "ic_vector_paintbrush_stroke.xml",
    "ic_vector_people_crowd.xml",
    "ic_vector_people_crowd_stroke.xml",
    "ic_vector_people_group.xml",
    "ic_vector_people_group_stroke.xml",
    "ic_vector_people_stroke.xml",
    "ic_vector_person.xml",
    "ic_vector_person_stroke.xml",
    "ic_vector_promoted_pill_stroke.xml",
    "ic_vector_reply.xml",
    "ic_vector_reply_stroke.xml",
    "ic_vector_rocket.xml",
    "ic_vector_rocket_stroke.xml",
    "ic_vector_safety.xml",
    "ic_vector_safety_fill.xml",
    "ic_vector_search.xml",
    "ic_vector_search_person_stroke.xml",
    "ic_vector_search_stroke.xml",
    "ic_vector_search_white.xml",
    "ic_vector_settings_stroke.xml",
    "ic_vector_share_android.xml",
    "ic_vector_spaces.xml",
    "ic_vector_spaces_stroke.xml",
    "ic_vector_sparkle.xml",
    "ic_vector_sparkle_stroke.xml",
    "ic_vector_speaker_off.xml",
    "ic_vector_stats_stroke.xml",
    "ic_vector_timeline_stroke.xml",
    "ic_vector_topics_close_stroke.xml",
    "ic_vector_topics_stroke.xml",
    "ic_vector_twitter.xml",
    "ic_vector_write.xml",
    "ic_vector_write_stroke.xml",
    "ic_vector_xchat_stroke.xml",
)

private val DRAWABLE_ANYDPI_V21 = arrayOf(
    "ic_vector_compose_dm.xml",
    "ic_vector_messages_arrow_left_stroke.xml",
    "ic_vector_notifications_follow.xml",
    "ic_vector_notifications_following.xml",
    "ic_vector_notifications_off.xml",
    "ic_vector_reply.xml",
    "ic_vector_reply_stroke.xml",
    "ic_vector_sparkle.xml",
)

private val DRAWABLE_V11 = arrayOf(
    "ic_vector_camera_shortcut.xml",
    "ic_vector_camera_stroke.xml",
    "ic_vector_home.xml",
    "ic_vector_home_stroke.xml",
)

private val DRAWABLE_V21 = arrayOf(
    "ic_vector_bookmark_plus_stroke.xml",
    "ic_vector_bookmark_stroke.xml",
    "ic_vector_camera_shortcut.xml",
    "ic_vector_camera_stroke.xml",
    "ic_vector_close.xml",
    "ic_vector_compose.xml",
    "ic_vector_compose_lists.xml",
    "ic_vector_follow_arrows.xml",
    "ic_vector_home.xml",
    "ic_vector_home_stroke.xml",
    "ic_vector_lightbulb_stroke_off.xml",
    "ic_vector_lightbulb_stroke_on.xml",
    "ic_vector_lightning.xml",
    "ic_vector_lightning_stroke.xml",
    "ic_vector_link.xml",
    "ic_vector_lists.xml",
    "ic_vector_lists_stroke.xml",
    "ic_vector_messages.xml",
    "ic_vector_messages_stroke.xml",
    "ic_vector_notifications.xml",
    "ic_vector_notifications_off.xml",
    "ic_vector_notifications_stroke.xml",
    "ic_vector_person.xml",
    "ic_vector_person_stroke.xml",
    "ic_vector_promoted_pill_stroke.xml",
    "ic_vector_reply.xml",
    "ic_vector_reply_stroke.xml",
    "ic_vector_search.xml",
    "ic_vector_search_stroke.xml",
    "ic_vector_search_white.xml",
    "ic_vector_settings_stroke.xml",
    "ic_vector_share_android.xml",
    "ic_vector_sparkle.xml",
    "ic_vector_sparkle_stroke.xml",
    "ic_vector_topics_close_stroke.xml",
    "ic_vector_topics_stroke.xml",
    "ic_vector_twitter.xml",
)

private val MIPMAP_ANYDPI_V26 = arrayOf(
    "ic_app_icon_10.xml",
    "ic_app_icon_10_round.xml",
    "ic_app_icon_11.xml",
    "ic_app_icon_11_round.xml",
    "ic_app_icon_12.xml",
    "ic_app_icon_12_round.xml",
    "ic_app_icon_13.xml",
    "ic_app_icon_13_round.xml",
    "ic_app_icon_14.xml",
    "ic_app_icon_14_round.xml",
    "ic_app_icon_2.xml",
    "ic_app_icon_2_round.xml",
    "ic_app_icon_3.xml",
    "ic_app_icon_3_round.xml",
    "ic_app_icon_4.xml",
    "ic_app_icon_4_round.xml",
    "ic_app_icon_5.xml",
    "ic_app_icon_5_round.xml",
    "ic_app_icon_6.xml",
    "ic_app_icon_6_round.xml",
    "ic_app_icon_7.xml",
    "ic_app_icon_7_round.xml",
    "ic_app_icon_8.xml",
    "ic_app_icon_8_round.xml",
)

@Suppress("unused")
val puiIconThemePatch =
    resourcePatch(
        name = "PUI theme icons",
        description = "Applies the PUI overlay: classic Twitter bird launcher icon, gray adaptive app-icon backgrounds, themed in-app vector icons and the Twitter app name (ported from PUIThemeTwitterIcon.apk).",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        execute {
            copyResources("pui", ResourceGroup("drawable", *DRAWABLE))
            copyResources("pui", ResourceGroup("drawable-anydpi-v21", *DRAWABLE_ANYDPI_V21))
            copyResources("pui", ResourceGroup("drawable-v11", *DRAWABLE_V11))
            copyResources("pui", ResourceGroup("drawable-v21", *DRAWABLE_V21))
            copyResources("pui", ResourceGroup("mipmap-anydpi-v26", *MIPMAP_ANYDPI_V26))

            // Launcher adaptive icon: background -> PUI blue, foreground/monochrome -> bird vector (overridden above)
            document("res/values/colors.xml").use { document ->
                val colors: NodeList = document.getElementsByTagName("color")
                for (i in 0 until colors.length) {
                    val node = colors.item(i)
                    if (node is Element && node.getAttribute("name") == "ic_launcher_background") {
                        node.textContent = "#FF2A99E1"
                    }
                }
            }

            // App name -> Twitter (default locale covers all languages via fallback, same as the overlay)
            document("res/values/strings.xml").use { document ->
                val strings: NodeList = document.getElementsByTagName("string")
                for (i in 0 until strings.length) {
                    val node = strings.item(i)
                    if (node is Element) {
                        val name = node.getAttribute("name")
                        if (name == "app_name" || name == "APP_NAME") {
                            node.textContent = "Twitter"
                        }
                    }
                }
            }
        }
    }
