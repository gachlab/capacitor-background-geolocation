// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM
//
// Phase 1 stub. Real implementation in Phase 3.

package com.josuelmm.capacitor.backgroundgeolocation;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "BackgroundGeolocation")
public class BackgroundGeolocationPlugin extends Plugin {

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value", "");
        JSObject ret = new JSObject();
        ret.put("value", value);
        call.resolve(ret);
    }
}
