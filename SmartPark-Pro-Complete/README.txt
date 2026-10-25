SMARTPARK PRO — COMPLETE VS CODE PROJECT
========================================

IMPORTANT: Open the folder named SmartPark-Pro-Complete in VS Code.
The four .java files must be directly visible in the VS Code Explorer.
Do NOT open the public folder by itself.

FILES
-----
Main.java             Java web server and request routing
ParkingService.java   Parking operations and API
Vehicle.java          Vehicle data and parking fee calculation
HttpUtils.java        HTTP / JSON helper functions
public/index.html     Dashboard and forms
public/style.css      Responsive professional styling and icon styling
public/script.js      Dashboard behavior and API requests
public/config.json    Vehicle categories and settings
public/favicon.svg    Browser tab icon
run.bat               One-click compile and launch for Windows
.vscode/tasks.json    VS Code build/run tasks

REQUIREMENTS
------------
Install JDK 11 or newer (JDK 17 recommended). Check with:
    java -version
    javac -version

EASIEST WAY TO RUN (WINDOWS)
----------------------------
1. Extract this ZIP completely.
2. In VS Code choose File > Open Folder.
3. Select the extracted SmartPark-Pro-Complete folder.
4. In the Explorer, confirm Main.java is visible at the top level.
5. In VS Code terminal run: .\run.bat
6. Keep the terminal open. Open http://localhost:8080 in your browser.

RUN MANUALLY
------------
In VS Code's terminal, ensure the current folder contains Main.java, then run:
    javac Main.java ParkingService.java Vehicle.java HttpUtils.java
    java Main

If the terminal says "file not found: Main.java", you are in the wrong folder.
Run `dir` and confirm Main.java is listed, or use File > Open Folder to open
SmartPark-Pro-Complete. Do not run javac from the parent Downloads folder.

IF PORT 8080 IS BUSY
--------------------
In PowerShell:
    $env:PORT=8081
    java Main
Then visit http://localhost:8081.

FEATURES
--------
- Dashboard summary and visual parking-bay grid
- Register vehicle and assign available bay
- Vehicle exit and fee calculation
- Search by registration number or owner
- View all currently parked vehicles
- Activity history and CSV report export
- Refresh dashboard and activity information
- Waiting queue when all spaces are occupied
- Vehicle types include car, motorcycle, scooter, SUV, van, pickup truck,
  auto rickshaw, electric vehicle, accessible vehicle and bicycle
- UI icons/symbols for navigation, status, actions and summary cards

DATA STRUCTURES
---------------
- LinkedHashMap for active vehicle lookup
- TreeSet for selecting the lowest available bay
- Queue / ArrayDeque for waiting vehicles
- ArrayList for activity history

NOTE
----
This is an educational local demo. Data is held in memory and resets when the
server restarts. It does not process real payments or connect to physical sensors.
