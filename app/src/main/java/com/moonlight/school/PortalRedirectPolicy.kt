package com.moonlight.school

/** Never follow or scrape unexpected redirects. Only classify the failure. */
internal object PortalRedirectPolicy {
    enum class Decision { AUTH, BLOCK, SKIP }

    fun classify(scheme: String?, host: String?, port: Int, userInfo: String?, path: String?): Decision {
        if (scheme != "https" || port !in listOf(-1, 443) || userInfo != null) return Decision.BLOCK
        if (host in setOf("konto.librus.pl", "api.librus.pl", "portal.librus.pl")) return Decision.AUTH
        if (host != "synergia.librus.pl") return Decision.BLOCK
        val route = path.orEmpty().lowercase(java.util.Locale.ROOT)
        if (listOf("loguj", "login", "logowanie", "wyloguj", "auth", "oauth", "captcha", "blokada", "access_denied", "brak_dostepu").any { route.contains(it) })
            return Decision.AUTH
        // A portal section may be unavailable for this account. Skip rather than read it.
        return Decision.SKIP
    }
}
