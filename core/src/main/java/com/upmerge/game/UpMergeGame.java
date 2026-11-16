package com.upmerge.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;

/** First native libGDX playable shell. Gameplay rules live in the platform-independent BoardEngine. */
public final class UpMergeGame extends ApplicationAdapter {
    private final BoardEngine engine = new BoardEngine();
    private OrthographicCamera camera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private float cell,left,bottom;
    private int nextValue=2;

    @Override public void create(){
        camera=new OrthographicCamera();shapes=new ShapeRenderer();batch=new SpriteBatch();font=new BitmapFont();font.getData().setScale(1.15f);
        font.setColor(0.94f,0.97f,1f,1f);
        Gdx.input.setCatchKey(Input.Keys.BACK,true);
    }
    @Override public void resize(int width,int height){
        camera.setToOrtho(false,width,height);camera.update();shapes.setProjectionMatrix(camera.combined);batch.setProjectionMatrix(camera.combined);
        cell=Math.max(1,Math.min(width/5f,height/7f));left=(width-cell*5)/2f;bottom=(height-cell*7)/2f;
    }
    @Override public void render(){
        float dt=Math.min(Gdx.graphics.getDeltaTime(),.05f);engine.update(dt);
        if(Gdx.input.justTouched()){
            float x=Gdx.input.getX(),y=Gdx.graphics.getHeight()-Gdx.input.getY();
            if(y>Gdx.graphics.getHeight()*.88f){engine.setDirection(engine.direction()==BoardEngine.Direction.UP?BoardEngine.Direction.DOWN:BoardEngine.Direction.UP);}
            else{int lane=MathUtils.clamp((int)((x-left)/cell),0,4);if(engine.launch(lane,nextValue))nextValue=nextValue>=128?2:nextValue*2;}
        }
        ScreenUtils.clear(.035f,.055f,.085f,1f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for(int r=0;r<7;r++)for(int c=0;c<5;c++){
            shapes.setColor((r+c)%2==0?.105f:.085f,(r+c)%2==0?.145f:.12f,(r+c)%2==0?.20f:.17f,1f);
            shapes.rect(left+c*cell+1,bottom+r*cell+1,cell-2,cell-2);
        }
        for(BoardEngine.Cube cube:engine.cubes()){
            float x=left+cube.lane*cell+cell*.08f,y=bottom+(6-cube.visualRow)*cell+cell*.08f;
            float hue=((float)(Math.log(cube.value)/Math.log(2.0))*.13f)%1f;
            shapes.setColor(.18f+.55f*hue,.48f+.25f*(1-hue),.78f-.35f*hue,1f);
            shapes.rect(x,y,cell*.84f,cell*.84f);
        }
        shapes.end();
        batch.begin();
        String direction=engine.direction()==BoardEngine.Direction.UP?"UP ↑":"DOWN ↓";
        font.draw(batch,"UpMerge  •  NATIVE ALPHA",20,Gdx.graphics.getHeight()-28);
        font.draw(batch,"Tap a column to drop   |   Tap top to change direction",20,Gdx.graphics.getHeight()-56);
        font.draw(batch,direction+"     NEXT: "+nextValue+"     SCORE: "+engine.score(),20,Gdx.graphics.getHeight()-84);
        for(BoardEngine.Cube cube:engine.cubes()){
            String value=Integer.toString(cube.value);float x=left+cube.lane*cell+cell*.08f,y=bottom+(6-cube.visualRow)*cell+cell*.52f;
            font.draw(batch,value,x+cell*.25f,y);
        }
        batch.end();
    }
    @Override public void dispose(){shapes.dispose();batch.dispose();font.dispose();}
}
