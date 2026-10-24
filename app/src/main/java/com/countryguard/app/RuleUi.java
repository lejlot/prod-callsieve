package com.countryguard.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

final class RuleUi {
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static TextView text(Context c,String value,int size,int color){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;
    }
    static LinearLayout row(LinearLayout parent,String marker,String title,String summary,String key,
                            SettingsStore settings,Runnable open,Runnable changed) {
        Context c=parent.getContext();int green=Color.rgb(20,107,92),ink=Color.rgb(24,44,40);
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c,4),dp(c,6),dp(c,4),dp(c,6));row.setMinimumHeight(dp(c,68));
        if(!marker.isEmpty()) {
            TextView badge=text(c,marker,14,green);badge.setGravity(Gravity.CENTER);
            GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(232,241,235));bg.setCornerRadius(dp(c,10));badge.setBackground(bg);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(c,32),dp(c,32));p.setMargins(0,0,dp(c,12),0);row.addView(badge,p);
        }
        LinearLayout words=new LinearLayout(c);words.setOrientation(LinearLayout.VERTICAL);words.setGravity(Gravity.CENTER_VERTICAL);words.setMinimumHeight(dp(c,48));
        TextView heading=text(c,title,16,ink);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);words.addView(heading);
        if(!summary.isEmpty()) {TextView detail=text(c,summary,12,Color.rgb(86,104,98));detail.setPadding(0,dp(c,3),dp(c,4),0);words.addView(detail);}
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        if(open!=null){
            words.setOnClickListener(v->open.run());heading.setOnClickListener(v->open.run());
            TextView arrow=text(c,"›",25,green);arrow.setGravity(Gravity.CENTER);arrow.setContentDescription("Configure "+title);
            arrow.setOnClickListener(v->open.run());row.addView(arrow,new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));
            row.setOnClickListener(v->open.run());
        }
        if(key!=null){
            Switch toggle=new Switch(c);toggle.setTag("toggle:"+key);toggle.setContentDescription(title);
            toggle.setMinWidth(dp(c,48));toggle.setMinHeight(dp(c,48));toggle.setChecked(settings.flag(key));
            row.addView(toggle);toggle.setOnCheckedChangeListener((v,on)->{settings.flag(key,on);if(changed!=null)changed.run();});
        }
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
        View divider=new View(c);divider.setBackgroundColor(Color.rgb(233,238,234));parent.addView(divider,new LinearLayout.LayoutParams(-1,dp(c,1)));
        return row;
    }
}
