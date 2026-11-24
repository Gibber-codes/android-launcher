package codes.gibber.launcher

import android.content.pm.ApplicationInfo

/** The home screen's sections, in the order they show. Hidden apps stay in the app list only. */
object Sections {
    const val HIDDEN = "Hidden"
    val ALL = listOf("Wallet", "Games", "Social", "Dev", "Work", "Media", "Tools", "Maps", "Other")

    private val KNOWN = mapOf(
        "Wallet" to listOf(
            "com.samsung.android.spay", "com.samsung.android.samsungpay.gear", "com.google.android.apps.walletnfcrel",
            "com.venmo", "com.squareup.cash", "com.paypal.android.p2pmobile", "com.zellepay.zelle",
            "com.chase.sig.android", "com.wf.wellsfargomobile", "com.infonow.bofa", "com.konylabs.capitalone",
            "com.citi.citimobile", "com.usaa.mobile.android.usaa", "com.navyfederal.android", "com.chime.chmapplication",
            "com.discoverfinancial.mobile", "com.americanexpress.android.acctsvcs.us", "com.coinbase.android",
            "com.robinhood.android", "com.klarna.android", "com.affirm.central", "com.creditkarma.mobile",
        ),
        "Social" to listOf(
            "com.instagram.android", "com.instagram.barcelona", "com.zhiliaoapp.musically", "com.snapchat.android",
            "com.facebook.katana", "com.facebook.orca", "com.twitter.android", "com.discord", "com.whatsapp",
            "com.reddit.frontpage", "org.telegram.messenger", "com.samsung.android.messaging",
            "com.google.android.apps.messaging", "com.pinterest", "tv.twitch.android.app", "com.bereal.ft",
        ),
        "Dev" to listOf(
            "com.github.android", "com.termux", "com.anthropic.claude", "com.openai.chatgpt",
            "com.google.android.apps.bard", "com.netlify.app", "com.vercel.app",
        ),
        "Work" to listOf(
            "com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email.provider",
            "com.google.android.calendar", "com.samsung.android.calendar", "com.Slack", "com.slack",
            "com.google.android.dialer", "com.samsung.android.dialer", "com.google.android.contacts",
            "com.samsung.android.app.contacts", "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets", "notion.id", "com.microsoft.teams",
            "us.zoom.videomeetings", "com.intuit.quickbooks", "com.squareup", "com.waveapps.mobile",
        ),
        "Media" to listOf(
            "com.google.android.youtube", "com.google.android.apps.youtube.music", "com.spotify.music",
            "com.netflix.mediaclient", "com.sec.android.gallery3d", "com.google.android.apps.photos",
            "com.sec.android.app.camera", "com.hulu.plus", "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient",
            "com.soundcloud.android", "com.apple.android.music", "com.zhiliaoapp.musically.go",
            "com.lemon.lvoverseas", "com.adobe.lrmobile",
        ),
        "Maps" to listOf(
            "com.google.android.apps.maps", "com.waze", "com.ubercab", "me.lyft.android", "com.ubercab.eats",
            "com.dd.doordash", "com.gasbuddy.mobile", "com.citymapper.app.release",
        ),
        "Tools" to listOf(
            "com.android.chrome", "com.sec.android.app.sbrowser", "com.android.vending", "com.android.settings",
            "com.sec.android.app.clockpackage", "com.sec.android.app.popupcalculator", "com.sec.android.app.myfiles",
            "com.google.android.apps.nbu.files", "com.samsung.android.app.notes", "com.google.android.keep",
            "com.google.android.calculator", "com.google.android.deskclock", "com.samsung.android.voicerecorder",
            "com.google.android.googlequicksearchbox", "com.samsung.android.app.goodlock",
        ),
    ).flatMap { (section, pkgs) -> pkgs.map { it to section } }.toMap()

    /** The section an app goes in: where you moved it, else a good guess from what it is. */
    fun of(app: AppEntry, moved: Map<String, String>, hubProjects: Set<String>): String {
        moved[app.key]?.let { return it }
        if (HubGames.isGame(app)) return if (HubGames.idOf(app) in hubProjects) "Dev" else "Games"
        if (HubLink.isOwn(app)) return "Dev"
        if (Shortcuts.isShortcut(app)) return "Other"
        KNOWN[app.packageName]?.let { return it }
        if (app.isGame) return "Games"
        return when (app.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_MAPS -> "Maps"
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_NEWS -> "Media"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Work"
            else -> "Other"
        }
    }
}
