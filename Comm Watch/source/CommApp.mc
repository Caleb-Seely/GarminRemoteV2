// CommApp.mc
// Copyright 2015-2016 by Garmin Ltd. or its subsidiaries.
// Subject to Garmin SDK License Agreement and Wearables
// Application Developer Agreement.
//

using Toybox.Application;
using Toybox.Communications;
using Toybox.WatchUi;
using Toybox.System;

var mailMethod;
var phoneMethod;
var hasDirectMessagingSupport = true;

class CommExample extends Application.AppBase {

    function initialize() {
        Application.AppBase.initialize();

        mailMethod = method(:onMail);
        phoneMethod = method(:onPhone);
        if(Communications has :registerForPhoneAppMessages) {
            Communications.registerForPhoneAppMessages(phoneMethod);
        } else if(Communications has :setMailboxListener) {
            Communications.setMailboxListener(mailMethod);
        } else {
            hasDirectMessagingSupport = false;
        }
    }

    // onStart() is called on application start up
    function onStart(state) {
    }

    // onStop() is called when your application is exiting
    function onStop(state) {
    }

    // Return the initial view of your application here
    function getInitialView() {
        return [new CommView(), new CommInputDelegate()];
    }

    function onMail(mailIter) {
        var mail = mailIter.next();

        while(mail != null) {
            // Show message in MessageView
            WatchUi.pushView(new MessageView(mail.toString()), null, WatchUi.SLIDE_IMMEDIATE);
            mail = mailIter.next();
        }

        Communications.emptyMailbox();
    }

    function onPhone(msg) {
        // Show message in MessageView
        WatchUi.pushView(new MessageView(msg.data.toString()), null, WatchUi.SLIDE_IMMEDIATE);
    }
}