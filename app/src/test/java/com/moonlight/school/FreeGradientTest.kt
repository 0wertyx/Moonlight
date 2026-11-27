package com.moonlight.school

import org.junit.Assert.*
import org.junit.Test

class FreeGradientTest {
    @Test fun positionsRoundTrip() {
        val points=List<GradientPoint?>(8) {if(it==3) GradientPoint(.25f,.7f) else null}
        assertEquals(points,GradientMesh.decodePositions(GradientMesh.encodePositions(points)))
        assertNull(GradientMesh.decodePositions("NaN:0.5")[0])
        assertNull(GradientMesh.decodePositions("1.1:0.5")[0])
    }
    @Test fun movedColorFollowsPoint() {
        val colors=GradientMesh.palette("#FF0000","#0000FF",emptyList())
        val saved=List<GradientPoint?>(8) {when(it){0->GradientPoint(1f,0f);4->GradientPoint(0f,1f);else->null}}
        val positions=GradientMesh.positions(2,0f,saved)
        val pixels=GradientMesh.renderFree(colors,2,positions,3,3)
        assertEquals(0xffff0000.toInt(),pixels[2])
        assertEquals(0xff0000ff.toInt(),pixels[6])
    }
    @Test fun inactivePositionRetained() {
        val saved=List<GradientPoint?>(8) {if(it==7) GradientPoint(.3f,.6f) else null}
        assertEquals(saved[7],GradientMesh.positions(2,0f,saved)[7])
        assertEquals(saved[7],GradientMesh.positions(8,0f,saved)[7])
    }
}
