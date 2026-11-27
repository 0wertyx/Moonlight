package com.moonlight.school

import org.junit.Assert.*
import org.junit.Test

class MailPathTest {
    @Test fun onlyExactMessagePath() {
        assertEquals("/wiadomosci/1/5/123",PortalSections.messagePath("/wiadomosci/1/5/123"))
        listOf("https://synergia.librus.pl/wiadomosci/1/5/123","/wiadomosci/2/5","/wiadomosci/1/5/123?x=1","/wiadomosci/1/5/../123").forEach { assertNull(PortalSections.messagePath(it)) }
    }
}
