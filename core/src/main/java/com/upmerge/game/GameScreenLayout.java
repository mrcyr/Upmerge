package com.upmerge.game;

/** Pixel-space gameplay layout. Every visual region and hit target comes from these bounds. */
final class GameScreenLayout {
    final float screenW,screenH,density;
    final float headerHeight,footerHeight,cell;
    final float boardLeft,boardBottom,boardTop,boardRight;
    final float statBottom,statHeight,pillBottom,pillHeight;
    final float queueBottom,queueHeight,queueLeft,queueCurrentWidth,queueNextWidth,queueGap;
    final float toolbarBottom,toolbarHeight,toolbarLeft,toolbarCardWidth,toolbarGap,hintBottom,hintHeight,pauseSize;

    GameScreenLayout(float width,float height,float density){
        screenW=Math.max(1f,width);screenH=Math.max(1f,height);this.density=Math.max(.75f,density);
        float d=this.density;
        // Proportions sampled from the supplied 259x554 gameplay reference.
        headerHeight=Math.max(158f*d,screenH*.285f);
        footerHeight=Math.max(116f*d,screenH*.21f);
        float gap=10f*d;
        statHeight=Math.min(58f*d,screenH*.071f);statBottom=screenH-120f*d;
        pillHeight=Math.min(38f*d,screenH*.056f);pillBottom=screenH-158f*d;
        queueHeight=Math.min(46f*d,screenH*.056f);
        queueCurrentWidth=screenW*.263f;queueNextWidth=screenW*.317f;queueGap=screenW*.027f;
        queueLeft=screenW*.525f-(queueCurrentWidth+queueGap+queueNextWidth)*.5f;
        toolbarHeight=Math.min(55f*d,screenH*.063f);toolbarBottom=screenH*.074f;
        toolbarLeft=screenW*.062f;toolbarGap=screenW*.027f;
        toolbarCardWidth=(screenW-2f*toolbarLeft-4f*toolbarGap)/5f;
        hintHeight=Math.min(18f*d,screenH*.036f);hintBottom=screenH*.025f;
        pauseSize=44f*d;
        float headerNeedBottom=Math.min(screenH-headerHeight,Math.min(pillBottom,statBottom));
        float footerContentTop=footerHeight-8f*d;
        float boardAreaBottom=footerContentTop+gap;
        float boardAreaTop=headerNeedBottom-7f*d;
        float usable=Math.max(1f,boardAreaTop-boardAreaBottom);
        float side=Math.max(8f*d,screenW*.025f);
        cell=Math.max(1f,Math.min((screenW-2f*side)/BoardEngine.COLUMNS,
                usable/BoardEngine.ROWS));
        float boardH=cell*BoardEngine.ROWS;
        boardLeft=(screenW-cell*BoardEngine.COLUMNS)*.5f;
        boardRight=boardLeft+cell*BoardEngine.COLUMNS;
        boardBottom=boardAreaBottom+(usable-boardH)*.5f;
        boardTop=boardBottom+boardH;
        // Queue sits immediately above the power row, matching the mockup.
        float queueBottomValue=Math.max(toolbarBottom+toolbarHeight+gap,
                screenH*.148f);
        queueBottom=Math.min(queueBottomValue,footerHeight-queueHeight-3f*d);
    }
    boolean boardContains(float x,float y){return x>=boardLeft&&x<=boardRight&&y>=boardBottom&&y<=boardTop;}
    int toolbarIndex(float x,float y){
        if(y<toolbarBottom||y>toolbarBottom+toolbarHeight)return -1;
        if (x < toolbarLeft || x > toolbarLeft + 5f * toolbarCardWidth + 4f * toolbarGap) return -1;
        int index = (int)((x - toolbarLeft) / (toolbarCardWidth + toolbarGap));
        if (index < 0 || index > 4) return -1;
        float cardX = toolbarLeft + index * (toolbarCardWidth + toolbarGap);
        return x <= cardX + toolbarCardWidth ? index : -1;
    }
    boolean pauseContains(float x,float y){return x>=screenW-pauseSize-8f*density&&y>=screenH-pauseSize-8f*density;}
}
