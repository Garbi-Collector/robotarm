
#define MyAppName "Robot Arm"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Garbi"
#define MyAppExeName "Robot Arm.exe"

[Setup]
AppId={{A83F142B-7E91-4B61-9A2D-6C2E8F410D73}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\Robot Arm
DefaultGroupName=Robot Arm
OutputDir=installers
OutputBaseFilename=RobotArm-Setup-1.0.0
SetupIconFile=assets\robotarm.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern

[Tasks]
Name: "desktopicon"; Description: "Crear un acceso directo en el escritorio"; GroupDescription: "Accesos directos:"; Flags: unchecked

[Files]
Source: "dist\Robot Arm\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Robot Arm"; Filename: "{app}\Robot Arm.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\Robot Arm"; Filename: "{app}\Robot Arm.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\Robot Arm.exe"; Description: "Ejecutar Robot Arm"; Flags: postinstall nowait skipifsilent
