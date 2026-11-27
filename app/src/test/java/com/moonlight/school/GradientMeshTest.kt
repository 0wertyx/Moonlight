package com.moonlight.school

import org.junit.Assert.*
import org.junit.Test

class GradientMeshTest {
    @Test fun countsAndMigration() {
        assertEquals(1,GradientMesh.leftCount(2))
        assertEquals(1,GradientMesh.rightCount(2))
        assertEquals(3,GradientMesh.leftCount(5))
        assertEquals(2,GradientMesh.rightCount(5))
        assertEquals(4,GradientMesh.leftCount(8))
        assertEquals(4,GradientMesh.rightCount(8))
        assertEquals(listOf("#112233","#112233","#112233","#112233","#445566","#445566","#445566","#445566"),
            GradientMesh.palette("#112233","#445566",emptyList()))
    }
    @Test fun cornersAndMiddle() {
        val p=intArrayOf(0xff000000.toInt(),0xff220000.toInt(),0xff440000.toInt(),0xff660000.toInt(),
            0xff0000ff.toInt(),0xff0022ff.toInt(),0xff0044ff.toInt(),0xff0066ff.toInt())
        assertEquals(p[0],GradientMesh.sample(p,8,0f,0f))
        assertEquals(p[3],GradientMesh.sample(p,8,0f,1f))
        assertEquals(p[4],GradientMesh.sample(p,8,1f,0f))
        assertEquals(p[7],GradientMesh.sample(p,8,1f,1f))
        assertEquals(0xff000080.toInt(),GradientMesh.sample(p,2,.5f,.5f))
    }
}
