/**
 * CommApp.mc
 * Main application class for the Communication Watch app.
 * Handles message reception and display functionality.
 * 
 * Features:
 * - Supports both phone app messages and mailbox messages
 * - Provides glance view for quick access
 * - Displays incoming messages in a dedicated view
 */

using Toybox.Application;
using Toybox.Communications;
using Toybox.WatchUi;
using Toybox.System;

// Global variables for message handling
var mailMethod;        // Method reference for mailbox messages
var phoneMethod;       // Method reference for phone app messages
var hasDirectMessagingSupport = true;  // Flag indicating if device supports direct messaging

class CommExample extends Application.AppBase {

    /**
     * Initialize the application
     * Sets up message handling methods based on device capabilities
     */
    function initialize() {
        Application.AppBase.initialize();

        mailMethod = method(:onMail);
        phoneMethod = method(:onPhone);
        
        // Check for phone app message support first, then fall back to mailbox
        if(Communications has :registerForPhoneAppMessages) {
            Communications.registerForPhoneAppMessages(phoneMethod);
        } else if(Communications has :setMailboxListener) {
            Communications.setMailboxListener(mailMethod);
        } else {
            hasDirectMessagingSupport = false;
        }
    }

    /**
     * Called when application starts
     * @param state Application state
     */
    function onStart(state) {
    }

    /**
     * Called when application stops
     * @param state Application state
     */
    function onStop(state) {
    }

    /**
     * Returns the initial view of the application
     * @return Array containing the main view and its delegate
     */
    function getInitialView() {
        return [new CommView(), new CommInputDelegate()];
    }

    /**
     * Returns the glance view for quick access
     * @return Array containing the glance view and its delegate
     */
    function getGlanceView() {
        return [new GlanceView(), new GlanceDelegate()];
    }

    /**
     * Handles incoming mailbox messages
     * @param mailIter Iterator for mailbox messages
     */
    function onMail(mailIter) {
        var mail = mailIter.next();

        while(mail != null) {
            // Show message in MessageView
            WatchUi.pushView(new MessageView(mail.toString()), null, WatchUi.SLIDE_IMMEDIATE);
            mail = mailIter.next();
        }

        Communications.emptyMailbox();
    }

    /**
     * Handles incoming phone app messages
     * @param msg Message received from phone app
     */
    function onPhone(msg) {
        // Show message in MessageView
        WatchUi.pushView(new MessageView(msg.data.toString()), null, WatchUi.SLIDE_IMMEDIATE);
    }
}