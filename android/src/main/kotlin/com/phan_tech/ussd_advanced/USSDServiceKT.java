/*
 * Copyright (c) 2020. BoostTag E.I.R.L. Romell D.Z.
 * All rights reserved
 * porfile.romellfudi.com
 */
package com.phan_tech.ussd_advanced;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;


/**
 * AccessibilityService object for ussd dialogs on Android mobile Telcoms
 *
 * @author Romell Dominguez
 * @version 1.1.c 27/09/2018
 * @since 1.0.a
 */
public class USSDServiceKT extends AccessibilityService {

    private static AccessibilityEvent event;

    /**
     * Premier résultat multisession : un seul {@code responseInvoke} (événements d'accessibilité dupliqués).
     */
    private void deliverMultisessionInitialResponse(USSDController ussd, AccessibilityEvent event) {
        // @JvmField sur Kotlin → champ public, pas de getter/setter Java
        if (ussd.multisessionInitialResponseDelivered) {
            Log.d("USSD-Service", "SKIP_DUPLICATE_MULTISESSION_INITIAL");
            return;
        }
        ussd.multisessionInitialResponseDelivered = true;
        USSDController.callbackInvoke.responseInvoke(event);
    }

    /**
     * Catch widget by Accessibility, when is showing at mobile display
     *
     * @param event AccessibilityEvent
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        USSDServiceKT.event = event;
        USSDController ussd = USSDController.INSTANCE;
//        Timber.d(String.format(
//                "onAccessibilityEvent: [type] %s [class] %s [package] %s [time] %s [text] %s",
//                event.getEventType(), event.getClassName(), event.getPackageName(),
//                event.getEventTime(), event.getText()));
        if (!ussd.isRunning()) {
            return;
        }
        String response = null;
        if(!event.getText().isEmpty()) {
            response = event.getText().get(0).toString();
        }
        boolean isLoadingState = isLoadingOrRunningMessage(response);
        boolean isIgnorableSystemState = isIgnorableSystemMessage(response);

        if (LoginView(event) && notInputText(event)) {
            if (isLoadingState || isIgnorableSystemState) {
                Log.d("USSD-Service", "KEEP_DIALOG_OPEN_ON_INTERMEDIATE_STATE (login/no-input): " + response);
                return;
            }
            clickOnButton(event, 0);
            ussd.stopRunning();
            USSDController.callbackInvoke.over(response != null ? response : "");
        } else if (problemView(event) || LoginView(event)) {
            clickOnButton(event, 1);
            USSDController.callbackInvoke.over(response != null ? response : "");
        } else if (isUSSDWidget(event)) {
            if (notInputText(event)) {
                if (isLoadingState || isIgnorableSystemState) {
                    Log.d("USSD-Service", "KEEP_DIALOG_OPEN_ON_INTERMEDIATE_STATE (ussd/no-input): " + response);
                    return;
                }
                // Menus sans EditText : ne pas appeler over() tant que la session multi est active,
                // sinon le 1er écran ferme la session et les sendMessage suivants ne sont plus traités.
                if (Boolean.TRUE.equals(ussd.getSendType()) && ussd.getCallbackMessage() != null) {
                    Log.d("USSD-Service", "MULTI_SESSION_POST_SEND_SCREEN (no-input): " + response);
                    ussd.getCallbackMessage().invoke(event);
                    return;
                }
                if (!Boolean.TRUE.equals(ussd.getSendType()) && Boolean.TRUE.equals(ussd.isRunning())) {
                    Log.d("USSD-Service", "MULTI_SESSION_FIRST_SCREEN (no-input): " + response);
                    deliverMultisessionInitialResponse(ussd, event);
                    return;
                }
                clickOnButton(event, 0);
                ussd.stopRunning();
                USSDController.callbackInvoke.over(response != null ? response : "");
            } else {
                if (Boolean.TRUE.equals(ussd.getSendType()))
                    ussd.getCallbackMessage().invoke(event);
                else
                    deliverMultisessionInitialResponse(ussd, event);
            }
        }

    }

    /**
     * Send whatever you want via USSD
     *
     * @param text any string
     */
    public static void send(String text) {
        setTextIntoField(event, text);
        clickOnButton(event, 1);
    }
    public static void send2(String text, AccessibilityEvent ev) {
        setTextIntoField(ev, text);
        clickOnButton(ev, 1);
    }

    /**
     * Dismiss dialog by using first button from USSD Dialog
     */
    public static void cancel() {
        clickOnButton(event, 0);
    }
    public static void cancel2(AccessibilityEvent ev) {
        clickOnButton(ev, 0);
    }

    /**
     * set text into input text at USSD widget
     *
     * @param event AccessibilityEvent
     * @param data  Any String
     */
    private static void setTextIntoField(AccessibilityEvent event, String data) {
        Bundle arguments = new Bundle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, data);
        }
        for (AccessibilityNodeInfo leaf : getLeaves(event)) {
            if (leaf.getClassName().equals("android.widget.EditText")
                    && !leaf.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                ClipboardManager clipboardManager = ((ClipboardManager)  USSDController
                        .INSTANCE.getContext().getSystemService(Context.CLIPBOARD_SERVICE));
                if (clipboardManager != null) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("text", data));
                }
                leaf.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            }
        }
    }

    /**
     * Method evaluate if USSD widget has input text
     *
     * @param event AccessibilityEvent
     * @return boolean has or not input text
     */
    protected static boolean notInputText(AccessibilityEvent event) {
        for (AccessibilityNodeInfo leaf : getLeaves(event))
            if (leaf.getClassName().equals("android.widget.EditText")) return false;
        return true;
    }

    private boolean isLoadingOrRunningMessage(String response) {
        if (response == null) return false;
        String msg = response.toLowerCase();
        return msg.contains("ussd code running")
                || msg.contains("running")
                || msg.contains("please wait")
                || msg.contains("wait")
                || msg.contains("loading")
                || msg.contains("en cours")
                || msg.contains("patientez")
                || msg.contains("veuillez patienter");
    }

    private boolean isIgnorableSystemMessage(String response) {
        if (response == null) return false;
        String msg = response.trim().toLowerCase();
        return msg.equals("phone services")
                || msg.equals("phone service")
                || msg.equals("services téléphoniques")
                || msg.equals("services telephoniques")
                || msg.equals("sim toolkit")
                || msg.equals("boîte à outils sim")
                || msg.equals("boite a outils sim");
    }

    /**
     * The AccessibilityEvent is instance of USSD Widget class
     *
     * @param event AccessibilityEvent
     * @return boolean AccessibilityEvent is USSD
     */
    private boolean isUSSDWidget(AccessibilityEvent event) {
        String className = event.getClassName() != null ? event.getClassName().toString() : "";
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        Log.d("USSD-Widget", "className=" + className + " pkg=" + packageName);

        // Direct class match for known USSD dialog classes
        if (className.equals("android.app.AlertDialog")
                || className.equals("android.app.Dialog")
                || className.equals("androidx.appcompat.app.AlertDialog")
                || className.equals("amigo.app.AmigoAlertDialog")
                || className.equals("com.android.phone.oppo.settings.LocalAlertDialog")
                || className.equals("com.zte.mifavor.widget.AlertDialog")
                || className.equals("color.support.v7.app.AlertDialog")
                || className.equals("miui.app.AlertDialog")
                || className.equals("com.samsung.android.app.SemAlertDialog")
                || className.equals("com.transsion.hubble.notification.AlertDialog")
                || className.equals("android.app.ProgressDialog")) {
            return true;
        }

        // Fallback: if the event comes from com.android.phone and contains text, treat as USSD
        if (packageName.equals("com.android.phone")
                || packageName.equals("com.android.server.telecom")
                || packageName.equals("com.samsung.android.dialer")
                || packageName.equals("com.mediatek.ims")) {
            if (event.getText() != null && !event.getText().isEmpty()) {
                return true;
            }
        }

        // Generic fallback: class name contains AlertDialog
        if (className.toLowerCase().contains("alertdialog") || className.toLowerCase().contains("ussd")) {
            return true;
        }

        return false;
    }

    /**
     * The View has a login message into USSD Widget
     *
     * @param event AccessibilityEvent
     * @return boolean USSD Widget has login message
     */
    private boolean LoginView(AccessibilityEvent event) {
        return isUSSDWidget(event)
                && USSDController.INSTANCE.getMap().get(USSDController.KEY_LOGIN)
                .contains(event.getText().get(0).toString());
    }

    /**
     * The View has a problem message into USSD Widget
     *
     * @param event AccessibilityEvent
     * @return boolean USSD Widget has problem message
     */
    protected boolean problemView(AccessibilityEvent event) {
        return isUSSDWidget(event)
                && USSDController.INSTANCE.getMap().get(USSDController.KEY_ERROR)
                .contains(event.getText().get(0).toString());
    }

    /**
     * click a button using the index
     *
     * @param event AccessibilityEvent
     * @param index button's index
     */
    protected static void clickOnButton(AccessibilityEvent event, int index) {
        int count = -1;
        for (AccessibilityNodeInfo leaf : getLeaves(event)) {
            if (leaf.getClassName().toString().toLowerCase().contains("button")) {
                count++;
                if (count == index) {
                    leaf.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        }
    }

    private static List<AccessibilityNodeInfo> getLeaves(AccessibilityEvent event) {
        List<AccessibilityNodeInfo> leaves = new ArrayList<>();
        if (event != null && event.getSource() != null) {
            getLeaves(leaves, event.getSource());
        }
        return leaves;
    }

    private static void getLeaves(List<AccessibilityNodeInfo> leaves, AccessibilityNodeInfo node) {
        if (node.getChildCount() == 0) {
            leaves.add(node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            getLeaves(leaves, node.getChild(i));
        }
    }

    /**
     * Active when SO interrupt the application
     */
    @Override
    public void onInterrupt() {
//        Timber.d( "onInterrupt");
    }

    /**
     * Configure accessibility server from Android Operative System
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d("USSD-Service", "onServiceConnected - configuring for Android " + Build.VERSION.SDK_INT);

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.notificationTimeout = 0;
            // Sur Android 13+, ne pas filtrer par packageNames pour capturer tous les dialogues USSD
            // car certains OEM utilisent des packages différents
            info.packageNames = null;
            setServiceInfo(info);
            Log.d("USSD-Service", "Service info updated: eventTypes=0x" + Integer.toHexString(info.eventTypes));
        }
    }
}
