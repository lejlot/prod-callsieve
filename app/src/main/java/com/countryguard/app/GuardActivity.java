package com.countryguard.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.*;

public abstract class GuardActivity extends Activity {
    protected final int GREEN = Color.rgb(20,107,92), INK = Color.rgb(24,44,40), MUTED = Color.rgb(86,104,98);
    protected SettingsStore settings;
    protected LinearLayout body;
    private ScrollView currentScroll;
    private String currentTitle="";
    protected Runnable backAction=this::finish;
    private android.app.AlertDialog detailDialog;
    @Override public void onCreate(android.os.Bundle state){
        super.onCreate(state);
        if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->backAction.run());
    }

    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){backAction.run();}

    protected int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    protected LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    protected void screen(String title) {
        int position=title.equals(currentTitle)&&currentScroll!=null?currentScroll.getScrollY():0;
        currentTitle=title;
        settings = new SettingsStore(this);
        FrameLayout outer = new FrameLayout(this); outer.setBackgroundColor(Color.rgb(245,247,244));
        outer.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = android.os.Build.VERSION.SDK_INT >= 30
                ? insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime())
                : insets.getSystemWindowInsets();
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        LinearLayout shell = column(); shell.setPadding(dp(20),dp(12),dp(20),0);
        outer.addView(shell, new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels,dp(760)),-1,Gravity.CENTER_HORIZONTAL));
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=RuleUi.text(this,"‹",32,GREEN);back.setGravity(Gravity.CENTER);back.setContentDescription("Back");back.setOnClickListener(v->backAction.run());
        toolbar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(52)));
        TextView heading=RuleUi.text(this,title,23,INK);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));shell.addView(toolbar);
        ScrollView scroll = new ScrollView(this); body = column(); body.setPadding(0,dp(8),0,dp(24)); scroll.addView(body);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(outer); outer.requestApplyInsets();
        currentScroll=scroll;scroll.post(()->scroll.scrollTo(0,position));
    }
    protected void row(LinearLayout box,String order,String title,String summary,String key,Runnable open,Runnable changed){
        RuleUi.row(box,order,title,summary,key,settings,open==null?null:()->{if(detailDialog!=null)detailDialog.dismiss();open.run();},changed);
    }
    protected void sheet(String title,java.util.function.Consumer<LinearLayout> fill){
        LinearLayout box=column();box.setPadding(dp(24),dp(12),dp(24),dp(16));fill.accept(box);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Done",null).create();
        detailDialog=dialog;
        dialog.show();dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,dp(760)),-2);

    }
    protected LinearLayout card(String title) {
        LinearLayout box = column(); box.setPadding(dp(16),dp(12),dp(16),dp(16));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(18)); box.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(8),0,dp(8)); body.addView(box,p);
        if (!title.isEmpty()) label(box,title,19,true); return box;
    }
    protected TextView label(LinearLayout box, String value, int size, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextColor(bold ? INK : MUTED); v.setTextSize(size);
        v.setPadding(0,dp(6),0,dp(6)); if (bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); box.addView(v); return v;
    }
    protected Button action(LinearLayout box,String title,Runnable run) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextColor(GREEN); b.setMinHeight(dp(48));
        b.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable shape=new GradientDrawable();shape.setColor(Color.rgb(232,241,235));shape.setCornerRadius(dp(12));b.setBackground(shape);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(4));
        box.addView(b,p); b.setOnClickListener(v -> run.run()); return b;
    }
    protected EditText field(LinearLayout box,String hint,boolean phone) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(16); e.setMinHeight(dp(52)); e.setSingleLine(true);
        e.setInputType(phone ? InputType.TYPE_CLASS_PHONE : InputType.TYPE_CLASS_TEXT); box.addView(e,new LinearLayout.LayoutParams(-1,-2)); return e;
    }
    protected void toggle(LinearLayout box,String label,String key,Runnable changed) {
        Switch s = new Switch(this); s.setText(label); s.setTextSize(16); s.setMinHeight(dp(56)); s.setChecked(settings.flag(key));
        box.addView(s,new LinearLayout.LayoutParams(-1,-2)); s.setOnCheckedChangeListener((b,on) -> {settings.flag(key,on); if(changed!=null) changed.run();});
    }
    protected void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
}
