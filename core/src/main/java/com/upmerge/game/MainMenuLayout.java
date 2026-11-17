package com.upmerge.game;

/** Single source of truth for menu geometry. All rectangles use libGDX bottom-left coordinates. */
final class MainMenuLayout {
    enum Target { MODE_CLASSIC, MODE_TIMED, MODE_CHALLENGE, DIRECTION_UP, DIRECTION_DOWN,
        THEMES, DAILY, CONTINUE, NEW_GAME, SHOP, ACHIEVEMENTS, STATISTICS, TASKS, NONE }

    static final class Rect {
        final float x, y, width, height;
        Rect(float x,float y,float width,float height){this.x=x;this.y=y;this.width=width;this.height=height;}
        float centerX(){return x+width*.5f;}
        float centerY(){return y+height*.5f;}
        boolean contains(float px,float py){return px>=x&&px<=x+width&&py>=y&&py<=y+height;}
        boolean overlaps(Rect r){return x<r.x+r.width&&x+width>r.x&&y<r.y+r.height&&y+height>r.y;}
    }

    final float screenW,screenH,density,contentWidth;
    final Rect[] modes=new Rect[3],directions=new Rect[2],menuActions=new Rect[2];
    final Rect continueButton,newGameButton;
    final Rect[][] utilities=new Rect[2][2];
    final Rect footer;

    MainMenuLayout(float width,float height,boolean saved){this(width,height,saved,1f);}
    MainMenuLayout(float width,float height,boolean saved,float density){
        screenW=Math.max(1f,width);screenH=Math.max(1f,height);this.density=Math.max(.75f,density);
        float margin=22f*this.density;
        float panelW=Math.min(390f*this.density,screenW-2f*margin);
        contentWidth=Math.max(100f*this.density,panelW-44f*this.density);
        float cx=screenW*.5f, gap=7f*this.density;
        float rowH=Math.min(46f*this.density,screenH*.07f);
        float modeW=(contentWidth-2f*gap)/3f;
        // Top-down anchors are converted once to bottom-left coordinates.
        float top=screenH*.285f;
        for(int i=0;i<3;i++) modes[i]=topRect(cx+(i-1)*(modeW+gap),top,modeW,rowH);
        float dirW=(contentWidth-gap)*.5f;
        top=screenH*.395f;
        for(int i=0;i<2;i++) directions[i]=topRect(cx+(i==0?-1:1)*(dirW+gap)*.5f,top,dirW,rowH);
        float half=(contentWidth-8f*this.density)*.5f;
        top=screenH*.485f;
        float actionOffset=(half+4f*this.density)*.5f;
        menuActions[0]=topRect(cx-actionOffset,top,half,rowH);
        menuActions[1]=topRect(cx+actionOffset,top,half,rowH);
        float mainH=Math.min(52f*this.density,screenH*.085f);
        if(saved){
            continueButton=topRect(cx,screenH*.57f,contentWidth,mainH);
            newGameButton=topRect(cx,screenH*.665f,contentWidth,mainH);
        }else{
            continueButton=null;
            newGameButton=topRect(cx,screenH*.615f,contentWidth,mainH);
        }
        float utilityW=half;
        float utilityTop0=screenH*.76f,utilityTop1=screenH*.835f;
        for(int row=0;row<2;row++) for(int col=0;col<2;col++){
            float yTop=row==0?utilityTop0:utilityTop1;
            utilities[row][col]=topRect(cx+(col==0?-1:1)*(utilityW+8f*this.density)*.5f,
                    yTop,utilityW,rowH);
        }
        footer=topRect(cx,screenH*.925f,contentWidth,22f*this.density);
    }

    private Rect topRect(float cx,float top,float w,float h){
        return new Rect(cx-w*.5f,screenH-top-h,w,h);
    }

    Target hit(float x,float y){
        for(int i=0;i<3;i++)if(modes[i].contains(x,y))return Target.values()[i];
        for(int i=0;i<2;i++)if(directions[i].contains(x,y))return i==0?Target.DIRECTION_UP:Target.DIRECTION_DOWN;
        for(int i=0;i<2;i++)if(menuActions[i].contains(x,y))return i==0?Target.THEMES:Target.DAILY;
        if(continueButton!=null&&continueButton.contains(x,y))return Target.CONTINUE;
        if(newGameButton.contains(x,y))return Target.NEW_GAME;
        for(int row=0;row<2;row++)for(int col=0;col<2;col++)if(utilities[row][col].contains(x,y)){
            if(row==0)return col==0?Target.STATISTICS:Target.TASKS;
            return col==0?Target.SHOP:Target.ACHIEVEMENTS;
        }
        return Target.NONE;
    }
}
