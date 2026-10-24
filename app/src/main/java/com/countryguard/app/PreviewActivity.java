package com.countryguard.app;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
public final class PreviewActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);startActivity(new Intent(this,RulesActivity.class).putExtra("test",true));finish();}
}
