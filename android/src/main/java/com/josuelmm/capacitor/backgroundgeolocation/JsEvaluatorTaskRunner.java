// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM
//
// Task runner that evaluates a user-supplied JS function in a hidden WebView
// when the Android background service fires a location/stationary/activity event
// while the host activity is killed (stopOnTerminate: false + headlessTask).
//
// Adapted from `com.marianhello.bgloc.cordova.headless.JsEvaluatorTaskRunner`
// (Apache-2.0) to use the Capacitor-namespaced `HeadlessTaskRegistry`.

package com.josuelmm.capacitor.backgroundgeolocation;

import android.content.Context;

import com.evgenii.jsevaluator.JsEvaluator;
import com.evgenii.jsevaluator.interfaces.JsCallback;
import com.marianhello.bgloc.headless.AbstractTaskRunner;
import com.marianhello.bgloc.headless.Task;

public class JsEvaluatorTaskRunner extends AbstractTaskRunner {

    public static final String BUNDLE_KEY = "JS";

    private JsEvaluator mJsEvaluator;

    public JsEvaluatorTaskRunner() {}

    @Override
    public void runTask(final Task task) {
        String headlessTask = HeadlessTaskRegistry.getInstance().getHeadlessTask();
        if (headlessTask == null) {
            task.onError("No headless task registered. Call BackgroundGeolocation.headlessTask(fn) first.");
            return;
        }

        JsCallback callback = new JsCallback() {
            @Override
            public void onResult(String value) {
                task.onResult(value);
            }

            @Override
            public void onError(String errorMessage) {
                task.onError(errorMessage);
            }
        };

        String jsTask = new StringBuilder()
                .append("function task(name, paramsString) {")
                .append("  var params = JSON.parse(paramsString);")
                .append("  var task = { name: name, params: params };")
                .append("  return (" + headlessTask + ")(task);")
                .append("}").toString();

        mJsEvaluator.callFunction(jsTask, callback, "task", task.getName(), task.toString());
    }

    @Override
    public void setContext(Context context) {
        super.setContext(context);
        mJsEvaluator = new JsEvaluator(context);
    }
}
