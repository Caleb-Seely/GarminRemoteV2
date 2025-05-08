//CommView.mc
// Copyright 2016 by Garmin Ltd. or its subsidiaries.
// Subject to Garmin SDK License Agreement and Wearables
// Application Developer Agreement.
//

using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Communications;
using Toybox.System;

class CommView extends WatchUi.View {
    var screenShape;

    function initialize() {
        View.initialize();
    }

    function onLayout(dc) {
        screenShape = System.getDeviceSettings().screenShape;
    }

    function onUpdate(dc) {
        dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
        dc.clear();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);

        if(hasDirectMessagingSupport) {
            dc.drawText(dc.getWidth() / 2, dc.getHeight() / 2, Graphics.FONT_MEDIUM, "Capture", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        } else {
            dc.drawText(dc.getWidth() / 2, dc.getHeight() / 3, Graphics.FONT_MEDIUM, "Direct Messaging API\nNot Supported", Graphics.TEXT_JUSTIFY_CENTER);
        }
    }
}