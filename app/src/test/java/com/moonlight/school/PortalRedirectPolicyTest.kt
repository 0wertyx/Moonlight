package com.moonlight.school
import org.junit.Assert.assertEquals
import org.junit.Test

class PortalRedirectPolicyTest {
    @Test fun sameOriginUnexpectedRouteIsSkipped() {
        assertEquals(PortalRedirectPolicy.Decision.SKIP, PortalRedirectPolicy.classify("https","synergia.librus.pl",-1,null,"/uczen_index"))
    }
    @Test fun authenticationAndUntrustedRedirectsStop() {
        assertEquals(PortalRedirectPolicy.Decision.AUTH, PortalRedirectPolicy.classify("https","konto.librus.pl",-1,null,"/login"))
        assertEquals(PortalRedirectPolicy.Decision.AUTH, PortalRedirectPolicy.classify("https","synergia.librus.pl",-1,null,"/loguj"))
        assertEquals(PortalRedirectPolicy.Decision.BLOCK, PortalRedirectPolicy.classify("https","other.example",-1,null,"/"))
        assertEquals(PortalRedirectPolicy.Decision.BLOCK, PortalRedirectPolicy.classify("http","synergia.librus.pl",-1,null,"/"))
    }
}
