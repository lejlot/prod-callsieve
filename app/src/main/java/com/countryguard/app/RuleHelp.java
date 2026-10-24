package com.countryguard.app;
import android.app.AlertDialog;
import android.content.Context;
public final class RuleHelp {
    public static void show(Context c,boolean sms){new AlertDialog.Builder(c).setTitle("Follow the branches").setMessage("Start at the top. Each decision follows its Yes or No connection. Either branch can contain another decision. An outcome ends that path.\n\nTap any outcome to add a decision there. Tap a decision to edit it, swap its branches, or keep one branch. Changes take effect only after Save.\n\nDrag to pan, pinch to zoom, or use Fit. Test highlights the route for a number and time.\n\nContacts means your phone address book. Country checks use the displayed number, which can be spoofed.\n\n"+(sms?"Inbox keeps and notifies. Filtered keeps the SMS without notification. Calls and SMS have independent trees.":"Allow lets the call through. Block rejects it. Silence lets it through without a ringtone. Android volume and Do Not Disturb still apply. Hidden numbers and internet calls are outside this app's screening." )).setPositiveButton("Close",null).show();}
}
