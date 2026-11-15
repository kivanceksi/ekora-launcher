#define MyAppVersion "0.3.2"
[Setup]
AppId={{FD2ADAF5-20B1-4E81-B735-7D4E87CDA784}
AppName=Ekora Launcher
AppVersion={#MyAppVersion}
DefaultDirName={localappdata}\Programs\EkoraLauncher
DefaultGroupName=Ekora Launcher
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
WizardStyle=modern
SetupIconFile=..\assets\logo.ico
UninstallDisplayIcon={app}\EkoraLauncher.exe
OutputDir=..\release
OutputBaseFilename=EkoraLauncher-Setup-0.3.2-Windows-x64
Compression=lzma2
SolidCompression=yes
CloseApplications=yes
[Languages]
Name: "turkish"; MessagesFile: "compiler:Languages\Turkish.isl"
[Tasks]
Name: "desktopicon"; Description: "Masaüstünde Ekora Launcher simgesi oluştur"; GroupDescription: "Kısayollar:"
[Files]
Source: "..\dist\EkoraLauncher\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{userprograms}\Ekora Launcher"; Filename: "{app}\EkoraLauncher.exe"
Name: "{userdesktop}\Ekora Launcher"; Filename: "{app}\EkoraLauncher.exe"; Tasks: desktopicon
[Run]
Filename: "{app}\EkoraLauncher.exe"; Description: "Ekora Launcher’ı aç"; Flags: nowait postinstall skipifsilent
; No UninstallDelete: users' worlds, logs and settings are preserved.
