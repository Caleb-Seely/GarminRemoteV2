# Requirements Document

## Introduction

The Review Prompt and Developer Note Visibility Fix addresses issues preventing users from seeing Google Play in-app review prompts and developer note popups when they should appear. The current problems include: (1) prompts being triggered too early in the DeviceActivity lifecycle before the user has successfully connected, (2) the Google Play review API failing silently in debug builds without proper fallback UI, (3) developer notes being perpetually deferred when review prompts are prioritized, and (4) incorrect timing constants set for testing that need to be restored to production values. The feature will ensure prompts appear at appropriate moments with proper timing, fallback mechanisms, and improved visibility.

## Requirements

### Requirement 1

**User Story:** As a user, I want review prompts to appear at the right time with proper timing intervals, so that I can provide feedback when I'm familiar with the app but not be overwhelmed with requests.

#### Acceptance Criteria

1. WHEN the app has been installed for 5 or more days THEN the system SHALL show the first review prompt when the user opens the app
2. WHEN a review prompt has been shown THEN the system SHALL wait 30 days before showing the next review prompt
3. WHEN review prompts have been shown 6 times total THEN the system SHALL stop showing review prompts permanently
4. WHEN the user completes a review THEN the system SHALL stop showing review prompts permanently
5. WHEN the Google Play review API is not available (debug builds) THEN the system SHALL show a visible fallback dialog that simulates the review experience

### Requirement 2

**User Story:** As a user, I want developer notes to appear at appropriate intervals without interfering with review prompts, so that I can learn about the developer when I'm not being asked for feedback.

#### Acceptance Criteria

1. WHEN the app has been installed for 5 or more days THEN the system SHALL show the first developer note when the user opens the app (if no review prompt is shown)
2. WHEN a developer note has been shown THEN the system SHALL wait 60 days before showing the next developer note version
3. WHEN all 4 developer note versions have been shown THEN the system SHALL stop showing developer notes permanently
4. WHEN both review prompt and developer note should show on the same day THEN the system SHALL show the review prompt and defer the developer note to the next day the user opens the app
5. WHEN a developer note is deferred THEN the system SHALL show it the next time the user opens the app (regardless of the 60-day interval)

### Requirement 3

**User Story:** As a user, I want prompts to appear after I've successfully connected to my device, so that I'm in a positive mindset when asked for feedback or shown developer information.

#### Acceptance Criteria

1. WHEN the DeviceActivity starts THEN the system SHALL NOT immediately trigger review prompts or developer notes
2. WHEN the companion app verification succeeds THEN the system SHALL trigger the timing check for prompts
3. WHEN the MessageService starts successfully THEN the system SHALL consider this a successful interaction moment for showing prompts
4. WHEN the app verification fails THEN the system SHALL NOT trigger any prompts
5. WHEN the device is not connected THEN the system SHALL NOT trigger any prompts

### Requirement 4

**User Story:** As a developer, I want to be able to see review prompts and developer notes in debug builds, so that I can test the functionality and verify the user experience.

#### Acceptance Criteria

1. WHEN the Google Play review API fails in debug builds THEN the system SHALL show a custom dialog that simulates the review experience
2. WHEN the custom review dialog is shown THEN the system SHALL include options to "Rate App" and "Not Now" that behave like the real review flow
3. WHEN "Rate App" is selected in the custom dialog THEN the system SHALL open the Play Store app page or show a toast if not available
4. WHEN the custom dialog is dismissed THEN the system SHALL update the review timing state as if a real review was completed
5. WHEN the fallback dialog is used THEN the system SHALL log that it was a debug/fallback experience for analytics

### Requirement 5

**User Story:** As a developer, I want the timing constants restored to production values and the prompt triggering moved to the appropriate lifecycle moment, so that the system works correctly for real users.

#### Acceptance Criteria

1. WHEN the InAppReviewUtils is configured THEN the system SHALL use 5 days for INITIAL_DELAY_DAYS and 30 days for DAYS_BETWEEN_REVIEW_REQUESTS
2. WHEN the DeveloperNoteManager is configured THEN the system SHALL use 5 days for INITIAL_DELAY_MS and 60 days for REPEAT_INTERVAL_MS
3. WHEN DeviceActivity.onCreate() executes THEN the system SHALL NOT call `checkTimingTriggersWithPriority()`
4. WHEN the `onApplicationInfoReceived` callback executes THEN the system SHALL call the timing check logic
5. WHEN the timing check is moved THEN the system SHALL maintain all existing error handling, logging, and analytics

### Requirement 6

**User Story:** As a developer, I want enhanced debugging capabilities to troubleshoot timing and visibility issues, so that I can ensure prompts work correctly in all scenarios.

#### Acceptance Criteria

1. WHEN debug methods exist THEN the system SHALL preserve their functionality and add new debugging features
2. WHEN timing conditions are checked THEN the system SHALL log detailed information about why prompts are or aren't shown
3. WHEN prompts fail to appear THEN the system SHALL log specific error information and fallback actions taken
4. WHEN testing is needed THEN the system SHALL provide methods to reset timing state and force prompts for testing
5. WHEN analytics are logged THEN the system SHALL include comprehensive context about timing, location, and success/failure states