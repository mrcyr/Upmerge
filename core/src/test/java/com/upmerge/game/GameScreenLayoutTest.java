package com.upmerge.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameScreenLayoutTest {
    @Test public void boardFitsOnlyBetweenHeaderAndFooter() {
        for(float[] d:new float[][]{{320,568,1},{349,749,1},{384,828,1},
                {432,960,1},{765,1226,1},{1080,2400,3}}){
            GameScreenLayout l=new GameScreenLayout(d[0],d[1],d[2]);
            assertEquals(l.cell*BoardEngine.COLUMNS,l.boardRight-l.boardLeft,.02f);
            assertEquals(l.cell*BoardEngine.ROWS,l.boardTop-l.boardBottom,.02f);
            assertTrue(l.boardLeft>=0f);assertTrue(l.boardRight<=l.screenW);
            assertTrue(l.boardBottom>l.footerHeight);
            assertTrue(l.boardTop<=l.screenH-l.headerHeight-0.01f);
            assertTrue(l.boardBottom<l.boardTop);
        }
    }

    @Test public void hudCardsDoNotOverlapEachOtherOrTheBoard(){
        for(float[] d:new float[][]{{320,568,1},{349,749,1},{384,828,1},
                {765,1226,1},{1080,2400,3}}){
            GameScreenLayout l=new GameScreenLayout(d[0],d[1],d[2]);
            assertTrue(l.statBottom>=l.pillBottom+l.pillHeight);
            assertTrue(l.statBottom+l.statHeight<=l.screenH);
            assertTrue(l.pillBottom>=l.screenH-l.headerHeight);
            assertTrue(l.pillBottom>l.boardTop);
            assertTrue(l.boardTop<=l.screenH-l.headerHeight);
            assertTrue(l.statBottom+l.statHeight<=l.screenH);
        }
    }

    @Test public void footerRowsAreSeparatedAndStayInsideFooter(){
        for(float[] d:new float[][]{{320,568,1},{349,749,1},{384,828,1},
                {765,1226,1},{1080,2400,3}}){
            GameScreenLayout l=new GameScreenLayout(d[0],d[1],d[2]);
            assertTrue(l.hintBottom>=0f);
            assertTrue(l.hintBottom+l.hintHeight<l.toolbarBottom);
            assertTrue(l.toolbarBottom+l.toolbarHeight<l.queueBottom);
            assertTrue(l.queueBottom+l.queueHeight<=l.footerHeight);
            assertTrue(l.toolbarIndex(d[0]*.1f,l.toolbarBottom+l.toolbarHeight*.5f)==0);
            assertTrue(l.toolbarIndex(d[0]*.9f,l.toolbarBottom+l.toolbarHeight*.5f)==4);
            assertEquals(-1,l.toolbarIndex(d[0]*.5f,l.toolbarBottom-1f));
            assertEquals(-1,l.toolbarIndex(d[0]*.5f,l.queueBottom));
        }
    }


    @Test public void gameplayAnchorsMatchTheSuppliedReferenceProportions() {
        GameScreenLayout l = new GameScreenLayout(259f, 554f, 1f);
        assertEquals(39.334f, l.statHeight, .02f);
        assertEquals(434f, l.statBottom, .02f);
        assertEquals(31.024f, l.pillHeight, .02f);
        assertEquals(396f, l.pillBottom, .02f);
        assertEquals(82.316f, l.queueBottom, .03f);
        assertEquals(31.024f, l.queueHeight, .02f);
        assertEquals(68.117f, l.queueCurrentWidth, .03f);
        assertEquals(82.103f, l.queueNextWidth, .03f);
        assertEquals(6.993f, l.queueGap, .02f);
        assertEquals(57.369f, l.queueLeft, .03f);
        assertEquals(40.996f, l.toolbarBottom, .02f);
        assertEquals(34.902f, l.toolbarHeight, .02f);
        assertEquals(13.85f, l.hintBottom, .02f);
        assertEquals(18f, l.hintHeight, .02f);
        assertEquals(38.65f, l.cell, .05f);
    }

    @Test public void pauseTargetIsInsideTopRightHeaderOnly(){
        GameScreenLayout l=new GameScreenLayout(384,828,1);
        assertTrue(l.pauseContains(380,810));
        assertFalse(l.pauseContains(20,810));
        assertFalse(l.pauseContains(380,l.screenH-l.headerHeight-1f));
    }

    @Test public void boardTapCoordinatesMapToValidColumns(){
        GameScreenLayout l=new GameScreenLayout(384,828,1);
        for(int c=0;c<BoardEngine.COLUMNS;c++){
            float x=l.boardLeft+(c+.5f)*l.cell;
            assertTrue(l.boardContains(x,(l.boardBottom+l.boardTop)*.5f));
        }
        assertFalse(l.boardContains(l.boardLeft-1f,(l.boardBottom+l.boardTop)*.5f));
    }
}
