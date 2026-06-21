package com.quickpdf;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.NativeInputEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

public class GlobalHotkeyListener implements NativeKeyListener {

    private static final Logger LOG = Logger.getLogger(GlobalHotkeyListener.class.getName());

    private final PopupController popup;

    // Tracks whether the popup is currently visible
    // so Alt+Q acts as a toggle
    private boolean popupVisible = false;

    public GlobalHotkeyListener(PopupController popup) {
        if (popup == null) throw new IllegalArgumentException("PopupController must not be null");
        this.popup = popup;
    }

    // ── Lifecycle ──────────────────────────────────────────────

    public void register() {
        try {
            // Silence JNativeHook's verbose logging
            Logger jnhLogger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            jnhLogger.setLevel(Level.WARNING);
            jnhLogger.setUseParentHandlers(false);

            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            LOG.info("Global hotkey listener registered (Alt+Q).");
        } catch (NativeHookException e) {
            LOG.severe("Failed to register native hook: " + e.getMessage());
        }
    }

    public void unregister() {
        try {
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.unregisterNativeHook();
            LOG.info("Global hotkey listener unregistered.");
        } catch (NativeHookException e) {
            LOG.warning("Failed to unregister native hook: " + e.getMessage());
        }
    }

    // ── Hotkey detection ───────────────────────────────────────

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        // Check Alt is held + Q is pressed
        boolean altHeld = (e.getModifiers() & NativeInputEvent.ALT_MASK) != 0;
        boolean qPressed = e.getKeyCode() == NativeKeyEvent.VC_Q;

        if (altHeld && qPressed) {
            toggle();
        }
    }

    // Required by NativeKeyListener — not used
    @Override public void nativeKeyReleased(NativeKeyEvent e) {}
    @Override public void nativeKeyTyped(NativeKeyEvent e) {}

    // ── Toggle ─────────────────────────────────────────────────

    private void toggle() {
        if (popupVisible) {
            popup.hide();
            popupVisible = false;
            LOG.info("Popup hidden via hotkey.");
        } else {
            popup.show();
            popupVisible = true;
            LOG.info("Popup shown via hotkey.");
        }
    }

    // Called by PopupController when [×] is clicked so toggle state stays in sync
    public void onPopupHidden() {
        popupVisible = false;
    }
}