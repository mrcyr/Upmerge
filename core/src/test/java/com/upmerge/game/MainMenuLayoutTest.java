package com.upmerge.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class MainMenuLayoutTest {
    @Test public void menuControlsAreCenteredAndTargetsAreDistinct() {
        for (float[] size : new float[][] {{320,568,1}, {360,800,1}, {430,932,1}, {1080,2400,3}}) {
            MainMenuLayout layout = new MainMenuLayout(size[0],size[1],true,size[2]);
            assertEquals(size[0] * .5f,
                    (layout.modes[0].centerX()+layout.modes[2].centerX())*.5f,.02f);
            assertEquals(size[0] * .5f,layout.modes[1].centerX(),.02f);
            assertFalse(layout.modes[0].overlaps(layout.modes[1]));
            assertFalse(layout.modes[1].overlaps(layout.modes[2]));
            assertFalse(layout.directions[0].overlaps(layout.directions[1]));
            assertFalse(layout.menuActions[0].overlaps(layout.menuActions[1]));
            assertFalse(layout.utilities[0][0].overlaps(layout.utilities[0][1]));
            assertFalse(layout.utilities[1][0].overlaps(layout.utilities[1][1]));
        }
    }

    @Test public void everyMainMenuControlHasItsOwnHitTarget() {
        MainMenuLayout layout = new MainMenuLayout(360,800,true);
        assertEquals(MainMenuLayout.Target.MODE_CLASSIC,layout.hit(layout.modes[0].centerX(),layout.modes[0].centerY()));
        assertEquals(MainMenuLayout.Target.MODE_TIMED,layout.hit(layout.modes[1].centerX(),layout.modes[1].centerY()));
        assertEquals(MainMenuLayout.Target.MODE_CHALLENGE,layout.hit(layout.modes[2].centerX(),layout.modes[2].centerY()));
        assertEquals(MainMenuLayout.Target.DIRECTION_UP,layout.hit(layout.directions[0].centerX(),layout.directions[0].centerY()));
        assertEquals(MainMenuLayout.Target.DIRECTION_DOWN,layout.hit(layout.directions[1].centerX(),layout.directions[1].centerY()));
        assertEquals(MainMenuLayout.Target.THEMES,layout.hit(layout.menuActions[0].centerX(),layout.menuActions[0].centerY()));
        assertEquals(MainMenuLayout.Target.DAILY,layout.hit(layout.menuActions[1].centerX(),layout.menuActions[1].centerY()));
        assertEquals(MainMenuLayout.Target.CONTINUE,layout.hit(layout.continueButton.centerX(),layout.continueButton.centerY()));
        assertEquals(MainMenuLayout.Target.NEW_GAME,layout.hit(layout.newGameButton.centerX(),layout.newGameButton.centerY()));
        assertEquals(MainMenuLayout.Target.SHOP,layout.hit(layout.utilities[0][0].centerX(),layout.utilities[0][0].centerY()));
        assertEquals(MainMenuLayout.Target.ACHIEVEMENTS,layout.hit(layout.utilities[0][1].centerX(),layout.utilities[0][1].centerY()));
        assertEquals(MainMenuLayout.Target.STATISTICS,layout.hit(layout.utilities[1][0].centerX(),layout.utilities[1][0].centerY()));
        assertEquals(MainMenuLayout.Target.TASKS,layout.hit(layout.utilities[1][1].centerX(),layout.utilities[1][1].centerY()));
    }

    @Test public void continueButtonExistsOnlyForSavedRun() {
        MainMenuLayout fresh=new MainMenuLayout(360,800,false);
        MainMenuLayout saved=new MainMenuLayout(360,800,true);
        assertNull(fresh.continueButton);
        assertNotNull(saved.continueButton);
        assertEquals(MainMenuLayout.Target.NEW_GAME,saved.hit(saved.newGameButton.centerX(),saved.newGameButton.centerY()));
    }

    @Test public void controlsStayInsideViewportAcrossDensitiesAndShortHeights() {
        for(float[] device:new float[][]{{320,568,1},{349,749,1},{384,828,1},{432,960,1},{1080,2400,3}}){
            MainMenuLayout l=new MainMenuLayout(device[0],device[1],true,device[2]);
            for(MainMenuLayout.Rect r:l.modes)inside(r,device[0],device[1]);
            for(MainMenuLayout.Rect r:l.directions)inside(r,device[0],device[1]);
            for(MainMenuLayout.Rect r:l.menuActions)inside(r,device[0],device[1]);
            inside(l.continueButton,device[0],device[1]);
            inside(l.newGameButton,device[0],device[1]);
            for(MainMenuLayout.Rect[] row:l.utilities)for(MainMenuLayout.Rect r:row)inside(r,device[0],device[1]);
            inside(l.footer,device[0],device[1]);
            assertTrue(l.modes[0].centerY()>l.directions[0].centerY());
            assertTrue(l.directions[0].centerY()>l.menuActions[0].centerY());
            assertTrue(l.menuActions[0].centerY()>l.continueButton.centerY());
            assertTrue(l.continueButton.centerY()>l.newGameButton.centerY());
            assertTrue(l.newGameButton.centerY()>l.utilities[0][0].centerY());
            assertTrue(l.utilities[0][0].centerY()>l.utilities[1][0].centerY());
            assertTrue(l.utilities[1][0].centerY()>l.footer.centerY());
        }
    }

    private static void inside(MainMenuLayout.Rect r,float w,float h){
        assertTrue("left",r.x>=0f);assertTrue("bottom",r.y>=0f);
        assertTrue("right",r.x+r.width<=w+.02f);
        assertTrue("top",r.y+r.height<=h+.02f);
    }
}
