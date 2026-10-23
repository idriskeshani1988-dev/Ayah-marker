package com.idris.ayahmarker;

import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SaveFilePlugin.class);
        super.onCreate(savedInstanceState);
        // Keep the screen on (no dimming, no turning off) while the app is open
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Back button: first let the page close its menu / lists (window.__idrisBack()).
        // Only when nothing is open does the app leave as usual.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                final OnBackPressedCallback self = this;
                if (getBridge() == null || getBridge().getWebView() == null) {
                    leaveApp(self);
                    return;
                }
                getBridge().getWebView().evaluateJavascript(
                    "(function(){try{return window.__idrisBack?window.__idrisBack():false}catch(e){return false}})()",
                    new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            if (!"true".equals(value)) {
                                leaveApp(self);   // nothing was open -> normal Back behaviour
                            }
                        }
                    });
            }
        });
    }

    private void leaveApp(OnBackPressedCallback cb) {
        cb.setEnabled(false);
        getOnBackPressedDispatcher().onBackPressed();
        cb.setEnabled(true);
    }
}
