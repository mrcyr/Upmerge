package com.upmerge.game;

/** Pixel-space gameplay layout. Every visual region and hit target comes from these bounds. */
final class GameScreenLayout {
    final float screenW,screenH,density;
    final float headerHeight,footerHeight,cell;
    final float boardLeft,boardBottom,boardTop,boardRight;
    final float statBottom,statHeight,pillBottom,pillHeight;
    final float queueBottom,queueHeight,queueWidth,queueGap;
    final float toolbarBottom,toolbarHeight,hintBottom,hintHeight,pauseSize;

    GameScreenLayout(float width,float height,float density){
        screenW=Math.max(1f,width);screenH=Math.max(1f,height);this.density=Math.max(.75f,density);
        float d=this.density;
        headerHeight=Math.max(174f*d,screenH*.145f);
        footerHeight=Math.max(154f*d,screenH*.14f);
        float gap=10f*d;
        statHeight=58f*d;statBottom=screenH-112f*d;
        pillHeight=38f*d;pillBottom=screenH-165f*d;
        queueHeight=46f*d;queueWidth=Math.min(112f*d,(screenW-32f*d-12f*d)*.5f);queueGap=12f*d;
        toolbarHeight=55f*d;toolbarBottom=31f*d;
        hintHeight=18f*d;hintBottom=7f*d;
        pauseSize=44f*d;
        float headerNeedBottom=Math.min(screenH-headerHeight,Math.min(pillBottom,statBottom));
        float footerContentTop=footerHeight-8f*d;
        float boardAreaBottom=footerContentTop+gap;
        float boardAreaTop=headerNeedBottom-gap;
        float usable=Math.max(1f,boardAreaTop-boardAreaBottom);
        float side=Math.max(8f*d,screenW*.025f);
        cell=Math.max(1f,Math.min((screenW-2f*side)/BoardEngine.COLUMNS,
                usable/BoardEngine.ROWS));
        float boardH=cell*BoardEngine.ROWS;
        boardLeft=(screenW-cell*BoardEngine.COLUMNS)*.5f;
        boardRight=boardLeft+cell*BoardEngine.COLUMNS;
        boardBottom=boardAreaBottom+(usable-boardH)*.5f;
        boardTop=boardBottom+boardH;
        float queueBottomValue=Math.max(toolbarBottom+toolbarHeight+gap,
                footerHeight-queueHeight-18f*d);
        // Keep queue, tools and hint within the footer, with explicit gaps.
        queueBottomValue=Math.min(queueBottomValue,footerHeight-queueHeight-6f*d);
        queueBottom=queueBottomValue;
    }
    boolean boardContains(float x,float y){return x>=boardLeft&&x<=boardRight&&y>=boardBottom&&y<=boardTop;}
    int toolbarIndex(float x,float y){
        if(y<toolbarBottom||y>toolbarBottom+toolbarHeight)return -1;
        return Math.max(0,Math.min(4,(int)(x/(screenW/5f))));
    }
    boolean pauseContains(float x,float y){return x>=screenW-pauseSize-8f*density&&y>=screenH-pauseSize-8f*density;}
}
