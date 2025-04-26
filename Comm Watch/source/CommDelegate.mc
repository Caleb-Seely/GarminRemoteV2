//CommDelegate.mc
// Copyright 2016 by Garmin Ltd. or its subsidiaries.
// Subject to Garmin SDK License Agreement and Wearables
// Application Developer Agreement.
//

using Toybox.WatchUi;
using Toybox.System;
using Toybox.Communications;

// Simple connection listener to handle message transmission results
class CommListener extends Communications.ConnectionListener {
    function initialize() {
        Communications.ConnectionListener.initialize();
    }

    function onComplete() {
        System.println("Message sent successfully");
    }

    function onError() {
        System.println("Error: Message transmission failed");
    }
}

// Main delegate to handle button presses
class CommInputDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        WatchUi.BehaviorDelegate.initialize();
    }

    // Handle action button press
    function onKey(keyEvent) {
        if (keyEvent.getKey() == WatchUi.KEY_ENTER) {
            System.println("Action button pressed - sending message");
            var listener = new CommListener();
            Communications.transmit("Hello", null, listener);
            return true;
        }
        return false;
    }
}


