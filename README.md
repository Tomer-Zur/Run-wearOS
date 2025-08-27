# Run Wear OS App

A Wear OS application for tracking running activities and syncing with the main Run app backend.
Main Run app - https://github.com/Eranshh/Run
Run app bakcend - https://github.com/avivjan/Run

## Features

- **Authentication**: Secure login system with JWT token-based authentication
- **Run Tracking**: Track your runs with GPS location, distance, pace, and calories, bpm and elevation
- **Data Sync**: Automatically sync run data to the backend server

### First Time Setup
In Android Studio, latest version:
1. If testing, create a new wearOS large round virtual device or pair a real device via wifi
2. Sync gradle files, build and launch the app on the device ( "run" on the top bar should do it for you)
3. Enter your username and password (same credentials as the main Run app - you will need to create a user there first)
4. Start a run with the ▶️ button, pause with ⏸️, when finished tap ⏹️ to end run and log data to the database
5. You can see the run details including track drawn on the map, elevation graph and other stats in the main app user's history page

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

### Known Issues
- If using android studio virtual device, giving premission for body sensor data will fail and "no prem" will be diplayed in bpm field

## License
- This project is licensed under the MIT License.