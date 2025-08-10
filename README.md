# Run Wear OS App

A Wear OS application for tracking running activities and syncing with the main Run app backend.

## Features

- **Authentication**: Secure login system with JWT token-based authentication
- **Run Tracking**: Track your runs with GPS location, distance, pace, and calories
- **Data Sync**: Automatically sync run data to the backend server
- **Offline Support**: Store credentials locally for seamless login experience

## Login System

The app implements a complete authentication flow:

### First Time Setup
1. Launch the app on your Wear OS device
2. Enter your username and password (same credentials as the main Run app)
3. Tap "Login" to authenticate
4. Your credentials are securely stored locally

### Subsequent Launches
- The app automatically checks for stored credentials
- If a valid token exists, you're logged in automatically
- If the token has expired, you'll be prompted to login again

### Logout
- Tap the "Logout" button to clear stored credentials
- You'll be returned to the login screen

## Technical Details

### Authentication Flow
1. **Login Request**: Sends username/password to `https://runfuncionapp.azurewebsites.net/api/login`
2. **Token Validation**: Validates stored tokens against `https://runfuncionapp.azurewebsites.net/api/validate-token`
3. **Credential Storage**: Uses Android SharedPreferences for secure local storage
4. **Automatic Refresh**: Validates token on app startup

### Data Sync
- Run data is sent to the backend with authentication headers
- Includes GPS coordinates, distance, duration, calories, and pace
- Automatic retry on network failures

### Security
- JWT tokens with 7-day expiration
- Secure credential storage using Android SharedPreferences
- HTTPS communication with the backend
- Automatic token validation and cleanup

## Development

### Prerequisites
- Android Studio
- Wear OS device or emulator
- Backend server running (Run-Backend)

### Building
1. Open the project in Android Studio
2. Sync Gradle dependencies
3. Build and install on your Wear OS device

### Testing
- Use the same username/password as your main Run app
- Test login/logout functionality
- Verify run data syncs to the backend
- Test offline scenarios

## Backend Integration

The app integrates with the existing Run backend:
- Uses the same authentication endpoints
- Sends run data to the same API
- Maintains user session consistency across devices

## Troubleshooting

### Login Issues
- Ensure you're using the correct username/password
- Check your internet connection
- Verify the backend server is running

### Data Sync Issues
- Check network connectivity
- Verify authentication token is valid
- Check backend server logs for errors

### App Crashes
- Clear app data and re-login
- Ensure all permissions are granted
- Check for Wear OS updates
