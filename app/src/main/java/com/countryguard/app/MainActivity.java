package com.countryguard.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(24, 44, 40);
    private static final int MUTED = Color.rgb(86, 104, 98);
    private static final int GREEN = Color.rgb(20, 107, 92);
    private static final int BG = Color.rgb(245, 247, 244);
    private static final int RED = Color.rgb(166, 53, 49);
    private SettingsStore settings;
    private LinearLayout content;
    private int page = 0;
    private String countryQuery = "";
    private final List<String> regions = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new SettingsStore(this);
        page = getIntent().getIntExtra("page", 0);
        regions.addAll(PhoneNumberUtil.getInstance().getSupportedRegions());
        regions.sort(Comparator.comparing(this::countryName));
        if (state != null) {
            page = state.getInt("page", 0);
            countryQuery = state.getString("query", "");
        }
    }

    @Override protected void onResume() { super.onResume(); render(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("page", page); out.putString("query", countryQuery);
        super.onSaveInstanceState(out);
    }

    private void render() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(BG);
        outer.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = android.os.Build.VERSION.SDK_INT >= 30
                ? insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout())
                : insets.getSystemWindowInsets();
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        LinearLayout shell = column();
        int available = getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams centered = new FrameLayout.LayoutParams(Math.min(available, dp(760)), -1, Gravity.CENTER_HORIZONTAL);
        outer.addView(shell, centered);
        shell.setPadding(dp(20), dp(14), dp(20), 0);
        TextView brand = text("CALLSIEVE", 12, GREEN, true);
        brand.setLetterSpacing(0.18f);
        shell.addView(brand);
        TextView title = text(new String[]{"Call filtering", "Decision tree", "Call history"}[page], 28, INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.setMargins(0, dp(10), 0, dp(18));
        shell.addView(title, titleParams);
        content = column();
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(0, dp(8), 0, dp(8));
        String[] names = {"Calls", "Tree", "History"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            Button tab = button(names[i], false);
            tab.setTextSize(12);
            tab.setTextColor(page == i ? Color.WHITE : MUTED);
            tab.setBackground(shape(page == i ? GREEN : BG, 14));
            tab.setSelected(page == i);
            tab.setOnClickListener(v -> { if(index==1){startActivity(new Intent(this,RulesActivity.class));return;}page = index; render(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1);
            p.setMargins(dp(2), 0, dp(2), 0); tabs.addView(tab, p);
        }
        shell.addView(tabs);
        if (page == 0) showHome(); else if (page == 1) showCountries(); else showHistory();
        setContentView(outer);
        outer.requestApplyInsets();
    }

    private boolean isSelected() {
        RoleManager role = getSystemService(RoleManager.class);
        return role != null && role.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
            && role.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    private void showHome() {
        LinearLayout body=scrollBody(content);
        boolean selected=isSelected(),active=selected&&settings.enabled();
        LinearLayout hero=card(body,GREEN);
        addText(hero,active?"Protection on":selected?"Protection paused":"Set up call protection",26,Color.WHITE,true);
        Switch toggle=new Switch(this);toggle.setText("Call filtering");toggle.setTextColor(Color.WHITE);toggle.setTextSize(16);toggle.setMinHeight(dp(48));toggle.setChecked(settings.enabled());
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        toggle.setTrackTintList(android.content.res.ColorStateList.valueOf(Color.rgb(135,187,173)));
        toggle.setOnCheckedChangeListener((v,on)->{settings.setEnabled(on);render();});hero.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
        if(!selected){Button setup=button("Enable call screening",false);setup.setOnClickListener(v->requestRole());hero.addView(setup,buttonParams());}
        else addText(hero,active?"Your saved call rules are active":"All calls pass through",13,Color.WHITE,false);
        LinearLayout controls=card(body,Color.WHITE);
        RuleUi.row(controls,"⑂","Call decision tree","Edit decisions, Yes / No branches and outcomes",null,settings,()->startActivity(new Intent(this,RulesActivity.class)),null);
        RuleUi.row(controls,"↗","Test a path","Highlight the route for a number and time",null,settings,this::testNumber,null);
        LinearLayout messages=card(body,Color.WHITE);
        RuleUi.row(messages,"✉","Messages",!SmsRepository.isDefault(this)?"SMS inbox not set up":settings.flag("smsEnabled")?"SMS filtering on":"SMS filtering off",null,settings,()->startActivity(new Intent(this,MessagesActivity.class)),null);
        boolean contacts=checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;
        if(!contacts)RuleUi.row(body,"!","Contacts access needed","Saved contacts currently bypass this app",null,settings,()->requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},12),null);
        RuleUi.row(body,"⚙","Settings & help","Number format, permissions and app limits",null,settings,this::settingsMenu,null);
        addText(body,"ON THIS PHONE · NO CLOUD · 3.1",11,MUTED,false);
    }
    private void settingsMenu(){
        new AlertDialog.Builder(this).setTitle("Settings & help").setItems(new String[]{
            "Local numbers: "+countryName(settings.homeRegion()),"App permissions","How rules combine","Android limitations","Open-source licenses"
        },(d,which)->{
            if(which==0)chooseHomeRegion();
            else if(which==1)startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
            else if(which==2)RuleHelp.show(this,false);
            else if(which==3)new AlertDialog.Builder(this).setTitle("Android limitations")
                .setMessage("Countries follow the displayed number, not the caller's location. Caller ID can be spoofed.\n\nHidden numbers are not sent to this app. Block them in Samsung Phone settings. Internet calls, including WhatsApp, use their own controls.\n\nWithout Contacts access, saved contacts bypass screening. Keep CallSieve selected as your caller ID & spam app.")
                .setPositiveButton("Close",null).show();
            else showLicenses();
        }).show();
    }

    private void requestRole() {
        RoleManager role = getSystemService(RoleManager.class);
        if (role == null || !role.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            new AlertDialog.Builder(this).setTitle("Call screening unavailable")
                .setMessage("This Android installation does not offer the call-screening role.")
                .setPositiveButton("OK", null).show(); return;
        }
        startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 11);
    }

    private void showLicenses() {
        LinearLayout body = dialogBody();
        ScrollView scroll = new ScrollView(this);
        TextView license = text("", 12, INK, false);
        StringBuilder value = new StringBuilder("Google libphonenumber and Android core-library desugaring\n\n");
        for (String file : new String[]{"libphonenumber-LICENSE.txt", "desugar_jdk_libs-LICENSE.txt"}) {
            value.append(file).append("\n\n");
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    getAssets().open("licenses/" + file), java.nio.charset.StandardCharsets.UTF_8))) {
                String line; while ((line = reader.readLine()) != null) value.append(line).append('\n');
            } catch (java.io.IOException e) { value.append("License unavailable. See the project’s licenses folder.\n"); }
            value.append("\n\n");
        }
        license.setText(value.toString()); scroll.addView(license);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, dp(380)));
        new AlertDialog.Builder(this).setTitle("Open-source licenses").setView(body).setPositiveButton("Close", null).show();
    }

    private void showCountries(){page=0;startActivity(new Intent(this,RulesActivity.class));}
    private void showHistory() {
        LinearLayout body=scrollBody(content);
        JSONArray entries=new HistoryStore(this).read();
        if(entries.length()==0){addText(body,"No calls checked yet",22,INK,true);addText(body,"Screened calls will appear here.",14,MUTED,false);return;}
        RuleUi.row(body,"","Clear history",entries.length()+" recent calls",null,settings,()->new AlertDialog.Builder(this).setTitle("Clear call history?")
            .setMessage("Your rules and Android call history stay unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{new HistoryStore(this).clear();render();}).show(),null);
        LinearLayout list=card(body,Color.WHITE);
        for(int i=0;i<entries.length();i++){
            JSONObject entry=entries.optJSONObject(i);if(entry==null)continue;
            String status=entry.optBoolean("blocked")?"Blocked":entry.optBoolean("silenced")?"Silenced":"Allowed";
            RuleUi.row(list,entry.optBoolean("blocked")?"×":entry.optBoolean("silenced")?"−":"✓",entry.optString("number","Unknown"),
                status+" · "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(entry.optLong("time"))),
                null,settings,()->historyDetail(entry),null);
        }
    }
    private void historyDetail(JSONObject entry){
        String number=entry.optString("number","Unknown"),n=new RuleEngine().normalizeException(number,settings.homeRegion());
        LinearLayout box=dialogBody();
        addText(box,entry.optBoolean("blocked")?"BLOCKED":entry.optBoolean("silenced")?"SILENCED":"ALLOWED",18,entry.optBoolean("blocked")?RED:GREEN,true);
        addText(box,entry.optString("reason"),15,MUTED,false);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(number).setView(box).setNegativeButton("Close",null).create();
        RuleUi.row(box,"⑂","Edit call tree","Change decisions and outcomes",null,settings,()->{dialog.dismiss();startActivity(new Intent(this,RulesActivity.class));},null);
        dialog.show();
    }

    private void testNumber() {
        startActivity(new Intent(this,PreviewActivity.class));
    }

    private void chooseHomeRegion() {
        LinearLayout layout = dialogBody();
        EditText search = input("Search country", false); layout.addView(search, buttonParams());
        ScrollView scroll = new ScrollView(this); LinearLayout list = column(); scroll.addView(list);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, dp(300)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Local number country").setView(layout)
            .setNegativeButton("Cancel", null).create();
        Runnable fill = () -> {
            list.removeAllViews(); String query = search.getText().toString().toLowerCase(Locale.ROOT);
            for (String iso : regions) {
                if (!(countryName(iso) + " " + iso).toLowerCase(Locale.ROOT).contains(query)) continue;
                Button choice = button(countryName(iso) + (settings.homeRegion().equals(iso) ? " · Selected" : ""), false);
                choice.setOnClickListener(v -> { settings.setHomeRegion(iso); dialog.dismiss(); render(); });
                list.addView(choice, buttonParams());
            }
        };
        watch(search, fill); fill.run(); dialog.show();
    }

    private String countryName(String iso) { return new Locale("", iso).getDisplayCountry(); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); return box; }
    private LinearLayout dialogBody() { LinearLayout box = column(); box.setPadding(dp(24), dp(8), dp(24), dp(8)); return box; }
    private LinearLayout scrollBody(LinearLayout parent) {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        LinearLayout body = column(); body.setPadding(0, 0, 0, dp(12)); scroll.addView(body);
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); return body;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private LinearLayout card(LinearLayout parent, int color) {
        LinearLayout box = column(); box.setBackground(shape(color, 20)); box.setPadding(dp(20), dp(16), dp(20), dp(18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, dp(14)); parent.addView(box, p); return box;
    }
    private TextView text(String label, int size, int color, boolean bold) {
        TextView text = new TextView(this); text.setText(label); text.setTextSize(size); text.setTextColor(color);
        text.setLineSpacing(dp(3), 1f); if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return text;
    }
    private void addText(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView text = text(value, size, color, bold);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(4), 0, dp(8)); parent.addView(text, p);
    }
    private Button button(String label, boolean primary) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(14);
        button.setTextColor(primary ? Color.WHITE : GREEN); button.setMinHeight(dp(48));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackground(shape(primary ? GREEN : Color.rgb(232, 241, 235), 12)); return button;
    }
    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(8), 0, dp(4)); return p;
    }
    private EditText input(String hint, boolean phone) {
        EditText field = new EditText(this); field.setHint(hint); field.setTextSize(15); field.setTextColor(INK);
        field.setSingleLine(true); field.setMinHeight(dp(52)); field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setBackground(shape(Color.rgb(232, 238, 233), 12));
        field.setInputType(phone ? InputType.TYPE_CLASS_PHONE : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return field;
    }
    private void watch(EditText field, Runnable action) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { action.run(); }
            @Override public void afterTextChanged(Editable value) { }
        });
    }
}
