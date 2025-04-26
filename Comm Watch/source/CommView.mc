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

    function drawIntroPage(dc) {
        
            dc.drawText(dc.getWidth() / 2, 50,  Graphics.FONT_SMALL, "Capture Photo", Graphics.TEXT_JUSTIFY_CENTER);

    }

    function onUpdate(dc) {
        dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
        dc.clear();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);

        if(hasDirectMessagingSupport) {
            if(page == 0) {
                drawIntroPage(dc);
            } else {
                var i;
                var y = 50;

                dc.drawText(dc.getWidth() / 2, 20,  Graphics.FONT_MEDIUM, "Strings Received:", Graphics.TEXT_JUSTIFY_CENTER);
                for(i = 0; i < stringsSize; i += 1) {
                    dc.drawText(dc.getWidth() / 2, y,  Graphics.FONT_SMALL, strings[i], Graphics.TEXT_JUSTIFY_CENTER);
                    y += 20;
                }
             }
         } else {
             dc.drawText(dc.getWidth() / 2, dc.getHeight() / 3, Graphics.FONT_MEDIUM, "Direct Messaging API\nNot Supported", Graphics.TEXT_JUSTIFY_CENTER);
         }
    }


}