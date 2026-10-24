package com.countryguard.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;
import java.util.function.Consumer;

public final class TreeCanvas extends View {
    private final Paint p=new Paint(3);
    private final Map<DecisionTree.Node,RectF> boxes=new LinkedHashMap<>();
    private final Set<String> highlighted=new HashSet<>();
    private DecisionTree.Node root;
    private final Consumer<DecisionTree.Node> edit;
    private final boolean sms;
    private float scale=1,tx,ty,lastX,lastY,downX,downY,width,height;
    private boolean moved;
    private final ScaleGestureDetector pinch;
    public TreeCanvas(Context c,boolean sms,Consumer<DecisionTree.Node> edit){super(c);this.sms=sms;this.edit=edit;setBackgroundColor(Color.rgb(241,245,244));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription("Decision tree. Drag to pan, pinch to zoom, tap a node to edit.");
        pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){zoom(d.getScaleFactor(),d.getFocusX(),d.getFocusY());moved=true;return true;}});
    }
    public void tree(DecisionTree.Node n,boolean fit){root=n;boxes.clear();width=leaves(n)*244f;height=0;layout(n,0,0);highlighted.clear();if(fit)post(this::fit);invalidate();}
    private int leaves(DecisionTree.Node n){return n.kind==DecisionTree.Kind.OUTCOME?1:leaves(n.yes)+leaves(n.no);}
    private float layout(DecisionTree.Node n,float left,int depth){float w=leaves(n)*244f,x=left+w/2;boxes.put(n,new RectF(x-108,depth*170+45,x+108,depth*170+141));height=Math.max(height,depth*170+170);if(n.kind!=DecisionTree.Kind.OUTCOME){layout(n.yes,left,depth+1);layout(n.no,left+leaves(n.yes)*244f,depth+1);}return x;}
    public void fit(){if(width==0||getWidth()==0)return;scale=Math.max(.025f,Math.min(getResources().getDisplayMetrics().density,Math.min((getWidth()-24f)/width,(getHeight()-24f)/height)));tx=(getWidth()-width*scale)/2;ty=16;invalidate();}
    public void zoom(float factor){zoom(factor,getWidth()/2f,getHeight()/2f);}
    private void zoom(float factor,float x,float y){float old=scale;scale=Math.max(.025f,Math.min(4.5f,scale*factor));tx=x-(x-tx)*scale/old;ty=y-(y-ty)*scale/old;invalidate();}
    public void highlight(DecisionTree.Result result){highlighted.clear();for(DecisionTree.Step s:result.path())highlighted.add(s.id());highlighted.add(result.terminalId());invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(root==null)return;c.save();c.translate(tx,ty);c.scale(scale,scale);
        RectF start=boxes.get(root);p.setColor(0xff536e65);p.setTextSize(12);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("START",start.centerX()-20,25,p);
        p.setColor(0xffd9e3df);for(float x=Math.max(0,(float)Math.floor(-tx/scale/24)*24);x<Math.min(width,(getWidth()-tx)/scale);x+=24)for(float y=Math.max(0,(float)Math.floor(-ty/scale/24)*24);y<Math.min(height,(getHeight()-ty)/scale);y+=24)c.drawCircle(x,y,1,p);
        for(var entry:boxes.entrySet()){DecisionTree.Node n=entry.getKey();RectF r=entry.getValue();if(n.kind!=DecisionTree.Kind.OUTCOME){edge(c,n,r,n.yes,true);edge(c,n,r,n.no,false);}}
        for(var entry:boxes.entrySet()){
            DecisionTree.Node n=entry.getKey();RectF r=entry.getValue();boolean end=n.kind==DecisionTree.Kind.OUTCOME;
            int color=!end?0xff284c65:n.action==ProtectionPolicy.Action.ALLOW?0xff087b60:n.action==ProtectionPolicy.Action.BLOCK?0xffb14449:0xff8c6623;
            p.setColor(Color.WHITE);p.setStyle(Paint.Style.FILL);c.drawRoundRect(r,14,14,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(highlighted.contains(n.id)?5:2);p.setColor(highlighted.contains(n.id)?0xffe39a16:color);c.drawRoundRect(r,14,14,p);p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(11);p.setColor(color);c.drawText(end?"OUTCOME":"DECISION",r.left+14,r.top+21,p);
            String title=DecisionTree.title(n);if(sms&&end)title=n.action==ProtectionPolicy.Action.ALLOW?"Inbox":"Filtered";
            p.setTextSize(16);p.setColor(0xff16352e);java.util.List<String> lines=wrap(title,188);float y=r.top+44;for(String line:lines){c.drawText(line,r.left+14,y,p);y+=19;}
            p.setTextSize(11);p.setTypeface(Typeface.DEFAULT);p.setColor(0xff536e65);String detail=end?"Tap to change / add decision":DecisionTree.detail(n);while(p.measureText(detail)>188&&detail.length()>3)detail=detail.substring(0,detail.length()-2)+"…";c.drawText(detail,r.left+14,r.bottom-11,p);
        }c.restore();
    }
    private java.util.List<String> wrap(String s,float w){java.util.List<String> lines=new ArrayList<>();String line="";for(String word:s.split(" ")){if(p.measureText(line+" "+word)>w&&!line.isEmpty()){lines.add(line);line=word;}else line+=(line.isEmpty()?"":" ")+word;}lines.add(line);return lines;}
    private void edge(Canvas c,DecisionTree.Node parent,RectF from,DecisionTree.Node child,boolean yes){RectF to=boxes.get(child);p.setColor(highlighted.contains(parent.id)&&highlighted.contains(child.id)?0xffe39a16:yes?0xff398775:0xff8c6a72);p.setStrokeWidth(2);p.setStyle(Paint.Style.STROKE);Path path=new Path();float mid=(from.bottom+to.top)/2;path.moveTo(from.centerX(),from.bottom);path.lineTo(from.centerX(),mid);path.lineTo(to.centerX(),mid);path.lineTo(to.centerX(),to.top);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);c.drawCircle(to.centerX(),to.top,3,p);p.setTextSize(13);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText(yes?"YES":"NO",to.centerX()+7,mid-6,p);}
    @Override public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN -> {lastX=downX=e.getX();lastY=downY=e.getY();moved=false;return true;}
        case MotionEvent.ACTION_MOVE -> {if(Math.abs(e.getX()-downX)+Math.abs(e.getY()-downY)>12)moved=true;if(!pinch.isInProgress()){tx+=e.getX()-lastX;ty+=e.getY()-lastY;invalidate();}lastX=e.getX();lastY=e.getY();return true;}
        case MotionEvent.ACTION_UP -> {if(!moved){float x=(e.getX()-tx)/scale,y=(e.getY()-ty)/scale;for(var en:boxes.entrySet())if(en.getValue().contains(x,y)){performClick();edit.accept(en.getKey());break;}}return true;}
    }return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    private int accessibleFocus=-1,hovered=-1;
    private DecisionTree.Node atIndex(int index){return index<0||index>=boxes.size()?null:new ArrayList<>(boxes.keySet()).get(index);}
    private void announce(int id,int type){android.view.accessibility.AccessibilityEvent event=android.view.accessibility.AccessibilityEvent.obtain(type);event.setSource(this,id);event.setPackageName(getContext().getPackageName());event.setClassName("android.widget.Button");getParent().requestSendAccessibilityEvent(this,event);}
    @Override public android.view.accessibility.AccessibilityNodeProvider getAccessibilityNodeProvider(){return new android.view.accessibility.AccessibilityNodeProvider(){
        @Override public android.view.accessibility.AccessibilityNodeInfo createAccessibilityNodeInfo(int id){
            android.view.accessibility.AccessibilityNodeInfo info=android.view.accessibility.AccessibilityNodeInfo.obtain();
            if(id==HOST_VIEW_ID){info.setSource(TreeCanvas.this);onInitializeAccessibilityNodeInfo(info);for(int i=0;i<boxes.size();i++)info.addChild(TreeCanvas.this,i);return info;}
            DecisionTree.Node n=atIndex(id);if(n==null)return null;RectF r=boxes.get(n);int[] location=new int[2];getLocationOnScreen(location);
            Rect bounds=new Rect(Math.round(r.left*scale+tx),Math.round(r.top*scale+ty),Math.round(r.right*scale+tx),Math.round(r.bottom*scale+ty));info.setBoundsInParent(bounds);bounds.offset(location[0],location[1]);info.setBoundsInScreen(bounds);
            info.setSource(TreeCanvas.this,id);info.setParent(TreeCanvas.this);info.setPackageName(getContext().getPackageName());info.setClassName("android.widget.Button");info.setEnabled(true);info.setClickable(true);info.setFocusable(true);info.setVisibleToUser(true);
            String name=sms&&n.kind==DecisionTree.Kind.OUTCOME?(n.action==ProtectionPolicy.Action.ALLOW?"Inbox":"Filtered"):DecisionTree.title(n);
            info.setContentDescription(name+". "+DecisionTree.detail(n)+(n.kind==DecisionTree.Kind.OUTCOME?"":". Yes: "+DecisionTree.title(n.yes)+". No: "+DecisionTree.title(n.no)));
            info.setAccessibilityFocused(accessibleFocus==id);info.addAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);info.addAction(accessibleFocus==id?android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);return info;
        }
        @Override public boolean performAction(int id,int action,android.os.Bundle args){DecisionTree.Node n=atIndex(id);if(n==null)return false;
            if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK){edit.accept(n);return true;}
            if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS){accessibleFocus=id;announce(id,android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);return true;}
            if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS){accessibleFocus=-1;announce(id,android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);return true;}return false;
        }
    };}
    @Override public boolean dispatchHoverEvent(MotionEvent e){android.view.accessibility.AccessibilityManager manager=getContext().getSystemService(android.view.accessibility.AccessibilityManager.class);if(manager==null||!manager.isTouchExplorationEnabled())return super.dispatchHoverEvent(e);
        int index=0,hit=-1;if(e.getAction()!=MotionEvent.ACTION_HOVER_EXIT)for(RectF r:boxes.values()){if(r.contains((e.getX()-tx)/scale,(e.getY()-ty)/scale)){hit=index;break;}index++;}
        if(hit!=hovered){if(hovered!=-1)announce(hovered,android.view.accessibility.AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);hovered=hit;if(hit!=-1)announce(hit,android.view.accessibility.AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);}return hit!=-1;
    }
}
