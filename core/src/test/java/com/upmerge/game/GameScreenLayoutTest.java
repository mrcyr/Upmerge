package com.upmerge.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameScreenLayoutTest {
    @Test public void arenaFitsCenteredFiveBySevenSquareGrid() {
        for (float[] device : new float[][] {
                {320,568,1}, {360,640,1}, {375,812,1}, {393,873,1},
                {432,960,1}, {1080,2400,3}
        }) {
            GameScreenLayout l = new GameScreenLayout(device[0],device[1],device[2]);
            assertEquals(BoardEngine.COLUMNS*l.cell,l.boardRight-l.boardLeft,.02f);
            assertEquals(BoardEngine.ROWS*l.cell,l.boardTop-l.boardBottom,.02f);
            assertEquals(l.cell,(l.boardRight-l.boardLeft)/BoardEngine.COLUMNS,.02f);
            assertEquals(l.boardLeft,(device[0]-l.cell*BoardEngine.COLUMNS)*.5f,.02f);
            assertTrue(l.boardBottom>=l.arenaBottom-.02f);
            assertTrue(l.boardTop<=l.arenaTop+.02f);
            assertTrue(l.cell>0f);
        }
    }

    @Test public void toolbarHasFiveSeparateTouchTargets() {
        GameScreenLayout l = new GameScreenLayout(375,812,1);
        for(int i=0;i<5;i++){
            float x=l.toolbarLeft+i*(l.toolbarCardWidth+l.toolbarGap)+l.toolbarCardWidth*.5f;
            float y=l.toolbarBottom+l.toolbarHeight*.5f;
            assertEquals(i,l.toolbarIndex(x,y));
        }
        assertEquals(-1,l.toolbarIndex(l.toolbarLeft-1,l.toolbarBottom+l.toolbarHeight*.5f));
    }

    @Test public void queueAndToolbarStayAboveBottomHint() {
        for(float[] device:new float[][]{{320,568,1},{360,800,1},{1080,2400,3}}){
            GameScreenLayout l=new GameScreenLayout(device[0],device[1],device[2]);
            assertTrue(l.queueBottom>l.toolbarBottom+l.toolbarHeight);
            assertTrue(l.hintBottom<l.toolbarBottom);
            assertTrue(l.queueLeft>=0f);
            assertTrue(l.queueLeft+l.queueCurrentWidth+l.queueGap+l.queueNextWidth<=device[0]+.02f);
        }
    }
}
