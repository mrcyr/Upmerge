package com.upmerge.game;

/**
 * Menu geometry translated from the HTML panel's flex column and CSS gaps.
 * Rectangles are in libGDX bottom-left screen coordinates.
 */
final class MainMenuLayout {
    enum Target { MODE_CLASSIC, MODE_TIMED, MODE_CHALLENGE, DIRECTION_UP, DIRECTION_DOWN,
        THEMES, DAILY, CONTINUE, NEW_GAME, SHOP, ACHIEVEMENTS, STATISTICS, TASKS, NONE }

    static final class Rect {
        final float x, y, width, height;
        Rect(float x, float y, float width, float height) {
            this.x=x; this.y=y; this.width=width; this.height=height;
        }
        float centerX(){return x+width*.5f;}
        float centerY(){return y+height*.5f;}
        boolean contains(float px,float py){return px>=x&&px<=x+width&&py>=y&&py<=y+height;}
        boolean overlaps(Rect other) {
            return other != null && x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }

    final float screenW,screenH,density,contentWidth,panelLeft,panelBottom,panelWidth,panelHeight;
    final Rect[] modes=new Rect[3],directions=new Rect[2],menuActions=new Rect[2];
    final Rect continueButton,newGameButton;
    final Rect[][] utilities=new Rect[2][2];
    final Rect footer;

    MainMenuLayout(float width,float height,boolean saved){this(width,height,saved,1f);}
    MainMenuLayout(float width,float height,boolean saved,float density){
        screenW=Math.max(1f,width); screenH=Math.max(1f,height);
        this.density=Math.max(.75f,density);
        float d=this.density, w=screenW/d, h=screenH/d;
        panelWidth=Math.min(390f,Math.max(1f,w-24f))*d;
        float horizontalPad=Math.max(14f,Math.min(22f,w*.05f));
        contentWidth=Math.max(1f,panelWidth-2f*horizontalPad*d);
        float verticalPad=Math.max(16f,Math.min(25f,w*.045f));
        float logoH=Math.max(38f,Math.min(48f,w*.105f));
        float subH=Math.max(48f,Math.min(62f,w*.155f));
        float rowH=38f, actionH=40f, buttonH=48f, utilityH=42f;
        float utilityGap=12f, bottomGap=18f;
        // Give the title breathing room and separate the control groups.
        // Extra vertical space is absorbed by verticalScale on short screens.
        float titleToControls = 24f;
        float groupGap = 16f;
        float heightDp=verticalPad*2f+logoH+3f+subH+titleToControls
                +rowH+groupGap+rowH+groupGap+actionH
                +(saved?12f+buttonH:0f)+14f+buttonH+14f
                +utilityH*2f+12f+bottomGap+24f;
        // Scale the complete vertical stack to the actual available viewport.
        // The previous .78 lower bound forced rows outside short screens.
        float verticalScale=Math.min(1f,Math.max(.55f,(h-2f*verticalPad)/Math.max(1f,heightDp)));
        float v=d*verticalScale;

        // The native menu uses the full safe viewport, like the HTML app's
        // 100svh root. Content remains in a centered max-width column.
        // The main menu panel now occupies the entire screen height.
        panelHeight=screenH;
        panelLeft=(screenW-panelWidth)*.5f;
        panelBottom=0f;

        float innerLeft=panelLeft+horizontalPad*d;
        float innerW=contentWidth;
        float top=screenH-panelBottom-panelHeight+verticalPad*v;
        top+=logoH*v+3f*v+subH*v+titleToControls*v;
        float modeGap=7f*d, modeW=(innerW-2f*modeGap)/3f;
        for(int i=0;i<3;i++)modes[i]=topRect(innerLeft+i*(modeW+modeGap),top,modeW,rowH*v);
        top+=rowH*v+groupGap*v;
        float dirGap=7f*d, dirW=(innerW-dirGap)*.5f;
        directions[0]=topRect(innerLeft,top,dirW,rowH*v);
        directions[1]=topRect(innerLeft+dirW+dirGap,top,dirW,rowH*v);
        top+=rowH*v+groupGap*v;
        menuActions[0]=topRect(innerLeft,top,dirW,actionH*v);
        menuActions[1]=topRect(innerLeft+dirW+dirGap,top,dirW,actionH*v);
        top+=actionH*v;
        if(saved){
            top+=8f*v;
            continueButton=topRect(innerLeft,top,innerW,buttonH*v);
            top+=buttonH*v;
        }else continueButton=null;
        top+=14f*v;
        newGameButton=topRect(innerLeft,top,innerW,buttonH*v);
        top+=buttonH*v+14f*v;
        float utilityGapPx=utilityGap*d, utilityW=(innerW-utilityGapPx)*.5f;
        for(int row=0;row<2;row++)for(int col=0;col<2;col++)
            utilities[row][col]=topRect(innerLeft+col*(utilityW+utilityGapPx),
                    top+row*(utilityH*v+utilityGapPx*verticalScale),utilityW,utilityH*v);
        top+=2f*utilityH*v+utilityGapPx*verticalScale+bottomGap*v;
        footer=topRect(innerLeft,Math.min(top,screenH-panelBottom-verticalPad*v),innerW,18f*v);
    }

    private Rect topRect(float x,float top,float w,float h){
        return new Rect(x,screenH-top-h,w,h);
    }

    Target hit(float x,float y){
        for(int i=0;i<3;i++)if(modes[i].contains(x,y))return Target.values()[i];
        for(int i=0;i<2;i++)if(directions[i].contains(x,y))return i==0?Target.DIRECTION_UP:Target.DIRECTION_DOWN;
        for(int i=0;i<2;i++)if(menuActions[i].contains(x,y))return i==0?Target.THEMES:Target.DAILY;
        if(continueButton!=null&&continueButton.contains(x,y))return Target.CONTINUE;
        if(newGameButton.contains(x,y))return Target.NEW_GAME;
        for(int row=0;row<2;row++)for(int col=0;col<2;col++)if(utilities[row][col].contains(x,y)){
            if(row==0)return col==0?Target.SHOP:Target.ACHIEVEMENTS;
            return col==0?Target.STATISTICS:Target.TASKS;
        }
        return Target.NONE;
    }
}