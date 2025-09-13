# Implementation Plan

- [x] 1. Create core data structures and content repository





  - Implement DeveloperNoteContent data class with version, title, message, and button text properties
  - Create DeveloperNoteContentRepository object with all 4 message variations as specified in requirements
  - Write unit tests for content repository to verify all versions return correct content
  - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [x] 2. Implement SharedPreferences state management





  - Create DeveloperNoteManager object with methods for timing calculations and state persistence
  - Implement shouldShowPopup() method that checks 5-day initial delay and 60-day intervals
  - Implement version progression logic (0→1→2→3→complete) with proper state tracking
  - Add methods for recording popup events and managing SharedPreferences keys
  - Write comprehensive unit tests for timing logic, version progression, and edge cases
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

- [x] 3. Create popup layout and styling resources





  - Design dialog layout XML with CardView, circular ImageView for avatar, TextViews for content, and action buttons
  - Add avatar image resource (Caleb's headshot) in appropriate drawable densities
  - Create fade-in and fade-out animation resources for smooth popup transitions
  - Add string resources for all popup content variations and button labels
  - Apply Material Design styling with rounded corners, elevation, and theme-consistent colors
  - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.6, 2.4_

- [x] 4. Implement DialogFragment with user interaction handling





  - Create DeveloperNoteDialogFragment extending DialogFragment with proper lifecycle management
  - Implement dialog creation with custom layout and styling application
  - Add click handlers for website button (opens CalebSeely.com in browser) and email button (opens email client)
  - Implement close button functionality with proper state recording
  - Add fade animations for dialog show/hide transitions
  - Write unit tests for dialog creation and user interaction handling
  - _Requirements: 1.1, 1.6, 2.1, 2.3, 4.1, 4.2, 4.3, 4.4, 6.1, 6.5, 6.6_

- [x] 5. Integrate popup triggering into DeviceActivity





  - Add popup check logic to DeviceActivity.onCreate() after existing initialization
  - Implement checkAndShowDeveloperNote() method that uses DeveloperNoteManager to determine if popup should show
  - Add proper error handling and logging for popup display failures
  - Ensure popup only shows when activity is in foreground and properly initialized
  - _Requirements: 1.1, 5.6, 6.4_

- [x] 6. Add analytics tracking and error handling





  - Integrate analytics events for popup shown, website clicked, email clicked, and popup dismissed
  - Add Firebase Crashlytics error logging for popup-related failures
  - Implement graceful error handling for image loading failures, intent failures, and dialog creation issues
  - Add logging for debugging popup timing and state management
  - _Requirements: 6.1, 6.2, 6.3, 6.4_
-

- [x] 7. Write comprehensive tests for popup system



  - Create integration tests for end-to-end popup flow from timing check to display
  - Write UI tests using Espresso for user interaction scenarios (button clicks, dismissal)
  - Add tests for popup state persistence across app restarts and configuration changes
  - Create tests for timing edge cases (clock changes, rapid app launches, version completion)
  - Test popup behavior during app backgrounding and foregrounding
  - _Requirements: 1.1, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 3.5, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_