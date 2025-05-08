//CommDelegate.mc
// Copyright 2016 by Garmin Ltd. or its subsidiaries.
// Subject to Garmin SDK License Agreement and Wearables
// Application Developer Agreement.
//

using Toybox.WatchUi;
using Toybox.System;
using Toybox.Communications;
using Toybox.Timer;
using Toybox.Graphics as Gfx;

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

// View to show sending message
class SendingView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    function onUpdate(dc) {
        dc.setColor(Gfx.COLOR_WHITE, Gfx.COLOR_BLACK);
        dc.clear();
        dc.drawText(dc.getWidth() / 2, dc.getHeight() / 2, Gfx.FONT_MEDIUM, "Sending...", Gfx.TEXT_JUSTIFY_CENTER | Gfx.TEXT_JUSTIFY_VCENTER);
    }
}

// Main delegate to handle button presses
class CommInputDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        WatchUi.BehaviorDelegate.initialize();
    }

    // Handle action button press
   function onKey(keyEvent) {
      var keyCode = keyEvent.getKey();
      
      System.println("Code: " + keyCode + ") " );
      
      if (keyCode == WatchUi.KEY_ENTER) {
         System.println("Action button pressed - sending message");
         
         // Show sending message
         WatchUi.pushView(new SendingView(), new WatchUi.BehaviorDelegate(), WatchUi.SLIDE_IMMEDIATE);
         
         // Schedule the message sending and view pop after 1 second
         var timer = new Timer.Timer();
         timer.start(method(:sendMessage), 1000, false);
         
         return true;
      }
      return false;
   }

   function onTap(clickEvent) {
      var coords = clickEvent.getCoordinates();
      System.println("TAP EVENT: x=" + coords[0] + ", y=" + coords[1] );
              // Show sending message
        WatchUi.pushView(new SendingView(), new WatchUi.BehaviorDelegate(), WatchUi.SLIDE_IMMEDIATE);
        
        // Schedule the message sending and view pop after 1 second
        var timer = new Timer.Timer();
        timer.start(method(:sendMessage), 1000, false);
        
        return true;
   }

   function onSwipe(swipeEvent) {
      System.println("SWIPE EVENT: " + swipeEvent.getDirection );
      return false;
   }

   function onPush(pushEvent) {
      System.println("PUSH EVENT: " );
      return false;
   }

   function onRelease(releaseEvent) {
      System.println("RELEASE EVENT: " );
      return false;
   }
      
      function sendMessage() as Void {
         var listener = new CommListener();
         Communications.transmit("Hello Phone, please hit that big capture btn for me", null, listener);
         WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
      }
   }


