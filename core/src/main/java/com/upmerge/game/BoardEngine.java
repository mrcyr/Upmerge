package com.upmerge.game;

import java.util.*;

/** Deterministic 5x7 lane engine ported from the 4.11 rules. Coordinates are integer cells. */
public final class BoardEngine {
    public static final int COLUMNS = 5, ROWS = 7;
    public static final float MERGE_DELAY_SECONDS = .460f;
    private static final float MOVE_CELLS_PER_SECOND = 19.6f;
    public enum Direction { UP, DOWN }
    public static final class Cube {
        public final long id;
        public int lane, row;
        public final int value;
        public float visualRow;
        public boolean moving, mergePending;
        Cube(long id, int lane, int row, int value, boolean moving, float spawnRow) {
            this.id=id; this.lane=lane; this.row=row; this.value=value; this.visualRow=spawnRow; this.moving=moving;
        }
    }
    public static final class MergeEvent {
        public final int value, count, lane, row;
        MergeEvent(int value,int count,int lane,int row){this.value=value;this.count=count;this.lane=lane;this.row=row;}
    }
    private final ArrayList<Cube> cubes = new ArrayList<>();
    private long nextId=1;
    private Direction direction=Direction.UP;
    private float pendingTime;
    private List<Cube> pendingGroup=List.of();
    private Cube pendingAnchor;
    private MergeEvent lastMerge;
    private int score;

    public List<Cube> cubes(){ return Collections.unmodifiableList(cubes); }
    public Direction direction(){return direction;}
    public void setDirection(Direction d){direction=Objects.requireNonNull(d);}
    public int score(){return score;}
    public MergeEvent consumeMergeEvent(){MergeEvent e=lastMerge;lastMerge=null;return e;}
    public void clear(){cubes.clear();pendingGroup=List.of();pendingAnchor=null;pendingTime=0;score=0;lastMerge=null;}

    /** @return false when this lane is busy/full/illegal. */
    public boolean launch(int lane,int value){
        if(lane<0||lane>=COLUMNS||!isPowerOfTwo(value)||!pendingGroup.isEmpty())return false;
        List<Cube> stack=lane(lane);
        if(stack.stream().anyMatch(c->c.moving))return false;
        int row=firstEmptyRow(stack);
        boolean forced=false;
        if(row<0){
            Cube edge=entryEdge(stack);
            if(edge==null||edge.value!=value)return false;
            row=edgeRow(stack); forced=true;
        }
        float spawnRow=direction==Direction.UP?ROWS+.7f:-.7f;
        Cube incoming=new Cube(nextId++,lane,row,value,true,spawnRow);
        if(forced){incoming.visualRow=row;Cube edge=entryEdge(stack);if(edge!=null){beginMerge(incoming,edge);return true;}}
        cubes.add(incoming);
        return true;
    }
    private int firstEmptyRow(List<Cube> stack){
        boolean[] used=new boolean[ROWS];for(Cube c:stack)if(c.row>=0&&c.row<ROWS)used[c.row]=true;
        if(direction==Direction.UP){for(int r=0;r<ROWS;r++)if(!used[r])return r;}
        else{for(int r=ROWS-1;r>=0;r--)if(!used[r])return r;}
        return -1;
    }
    private int edgeRow(List<Cube> stack){Cube edge=entryEdge(stack);return edge==null?0:edge.row;}
    private Cube entryEdge(List<Cube> stack){int r=direction==Direction.UP?ROWS-1:0;for(Cube c:stack)if(c.row==r)return c;return null;}
    private List<Cube> lane(int lane){ArrayList<Cube> out=new ArrayList<>();for(Cube c:cubes)if(c.lane==lane)out.add(c);return out;}

    public void update(float dt){
        if(dt<=0)return;dt=Math.min(dt,.05f);
        boolean moved=false;
        for(Cube c:cubes){if(!c.moving)continue;float target=c.row;float delta=target-c.visualRow;float step=MOVE_CELLS_PER_SECOND*dt;if(Math.abs(delta)<=step){c.visualRow=target;c.moving=false;}else c.visualRow+=Math.signum(delta)*step;moved=true;}
        if(moved){for(int lane=0;lane<COLUMNS;lane++)compactLane(lane);}
        if(!pendingGroup.isEmpty()){
            pendingTime+=dt;
            if(pendingTime>=MERGE_DELAY_SECONDS)finishMerge();
            return;
        }
        ArrayList<Cube> scan=new ArrayList<>(cubes);scan.sort((a,b)->Long.compare(b.id,a.id));
        for(Cube c:scan){if(c.moving)continue;List<Cube> group=connectedGroup(c);if(group.size()>1){beginMerge(group,c);return;}}
    }
    private void compactLane(int lane){
        List<Cube> stack=lane(lane);stack.sort(Comparator.comparingInt(c->c.row));
        if(direction==Direction.DOWN)Collections.reverse(stack);
        for(int i=0;i<stack.size();i++){Cube c=stack.get(i);int target=direction==Direction.UP?i:ROWS-1-i;if(c.row!=target){c.row=target;c.moving=true;}}
    }
    private List<Cube> connectedGroup(Cube anchor){
        ArrayList<Cube> group=new ArrayList<>();HashSet<Long> seen=new HashSet<>();group.add(anchor);seen.add(anchor.id);
        for(int i=0;i<group.size();i++){Cube a=group.get(i);for(Cube b:cubes){if(seen.contains(b.id)||b.moving||b.value!=anchor.value)continue;if(Math.abs(a.lane-b.lane)+Math.abs(a.row-b.row)==1){seen.add(b.id);group.add(b);}}}
        return group;
    }
    private void beginMerge(List<Cube> group){beginMerge(group,group.get(0));}
    private void beginMerge(Cube incoming,Cube edge){ArrayList<Cube> group=new ArrayList<>();group.add(incoming);group.addAll(connectedGroup(edge));beginMerge(group,incoming);}
    private void beginMerge(List<Cube> group,Cube anchor){pendingGroup=List.copyOf(group);pendingAnchor=anchor;pendingTime=0;for(Cube c:pendingGroup)c.mergePending=true;}
    private void finishMerge(){
        int count=pendingGroup.size(),base=pendingGroup.get(0).value;int result=mergeValue(base,count);
        if(result>0){int lane=pendingAnchor.lane,row=pendingAnchor.row;for(Cube c:pendingGroup)cubes.remove(c);Cube made=new Cube(nextId++,lane,row,result,false,row);cubes.add(made);score+=result;lastMerge=new MergeEvent(result,count,lane,row);}
        pendingGroup=List.of();pendingAnchor=null;pendingTime=0;
        for(int lane=0;lane<COLUMNS;lane++)compactLane(lane);
    }
    public static int mergeValue(int value,int count){if(!isPowerOfTwo(value)||count<2)return 0;long total=(long)value*count;if(total>Integer.MAX_VALUE)return 0;long v=1;while(v<total)v<<=1;return v>Integer.MAX_VALUE?0:(int)v;}
    private static boolean isPowerOfTwo(int n){return n>=2&&(n&(n-1))==0;}
    public boolean isFull(){for(int l=0;l<COLUMNS;l++)if(firstEmptyRow(lane(l))>=0)return false;return true;}
}
