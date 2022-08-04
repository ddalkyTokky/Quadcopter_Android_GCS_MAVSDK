GoogleMap Mavsdk Android Test App
User Guide
AuthorAutonomia (KR) Inc.Lee Soon Yong

1 - 1. MAVSDK & Software
 In this project, we developed this app for GCS system. (Ground Control System) We used Opensource project MAVSDK. The target device is Pixhawk4. Look at the link below for Mavsdk.
- https://mavsdk.mavlink.io/main/en/index.html
 
 We used two base projects for this Test app. The project makes Android communicates with Usb devices through Serial_comm. - https://github.com/mik3y/usb-serial-for-android

 The project makes Android communicates with Drone or SITL, Telemetry Radio SIK through MAVSDK_SERVER & TCP_SERVER.
 - https://github.com/divyanshu1234/MavsdkAndroidSerialTest/tree/tcp_server

 The finished Android Test app in github.
- https://github.com/ddalkyTokky/GoogleMapMavsdk

1 - 2. Communication & Hardware
 The goal of GCS development TestApp is to build up the communication between 'Android' device and the drone based on 'Pixhawk4' FC. We are going to use Holybro V3 Telemetry Radio device, instead of using direct USB connection between Android device and Pixhawk4 drone. Therfore, appropriate Usb cable connection between Android device and Telemetry Radio SIK must be needed.
2 - 1. Test App Functions
2 - 1 - 1. Commands
 - Arm
 Arm the drone.
 - Takeoff
 Takeoff the drone. The function have 1 float parameter. Takeoff Altitude.
 - Land
 Land the drone.
 - Return
 Return the drone. The function have 1 float parameter. RTL_RETURN_ALT. defualt is maybe 25~30m. Check out for Pixhawk4 forum to be sure.
 - Kill 
 Disarm the drone permanently, until you press 'Arm' again.
 - UploadMission
 UploadMission to the drone. When you touch the screen, 'cyan' color markers will be made. Those markers will be the waypoints of the missoin. Check for the code about “Mission_ALT” and “Mission_Speed”.
 - StartMission
 Start the Mission. If the drone doesnt comeback even after the mission finished, press “PauseMission” and “Return”.
 - PauseMission
 Pause Mission. The drone will hover at current position.
 - ClearMission Clear Mission on the drone.
 - Camera
 Taking a photo. This Function is not checked yet. becareful about to use it.
 - SetGeofence
 Check for Parameters before you use this function. “FenceType.INCLUSION” means “inside the polygon” is forbidden flight area. Just like UploadMission, cyan Markers will be the polygon points. Be careful about order of Markers. If you try to draw squre Geofence, according to the order of markers, it could be “sandglass” shape.
 - ClearGeofence
 I could not find clearGeofence Function in Mavsdk Android. So I occuer error on SetGeofence to cause Error. In this case, existing Geofence is deleted.
2 - 1 - 2. Data Floating
 - WHERE?
 Position Of the drone. Red marker will move. And Altitude will appear through Toast message.
 - MissionProgress
 Toast message. Total Mission / Current Mission Index. when those are same, I make the app calls “Return”. You could delete this code if you want. if (total == current) “Mission Finished.”
 - FlightMode
 Show current FlightMode.
 - Battery
 Show current Battery remaining percentage & Battery voltage. 
 - Speed
 Show current XY speed and Y speed of the drone.
 - Attitude
 Show current Roll, Pitch, Yaw of the drone. Yaw is same with Compass direction.
 - RcStatus
 Connection status betweeen RC controller & drone. But this functoin doesnt work appropriately.
 - DroneConnectionState
 ConnectoinState between App & drone. This works fine.
2 - 1 - 3. etc.
 - CONNECT
 Check for Usb Connection with Telemetry Radio SIK. You have to press it until you have the message “Usb Connected” or “Working Connection Exist.”. The list of Available Telemetry devices or other Usb devices are comment below.
https://github.com/mik3y/usb-serial-for-android/blob/master/usbSerialExamples/src/main/res/xml/device_filter.xml
 - PrintErrorMessage
 Look at the codes on “2 - 1 - 1. Commands”. This function must be called after you make commands on the drone. This function will show you Complete message or Error Message. After you press it, this function will be reset. So you can see Complete/Error message only once.
