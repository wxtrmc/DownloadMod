# Project-Sturctare

## File tree

    downloadmod/
      build.gradle
      gradle.properties
      settings.gradle
      gradlew
      gradlew.bat
      gradle/wrapper/
      README.md
      ReadmeModrinth.md
      ReadmeCurseForge.md
      Project-Sturctare.md
      WhatFile.md
      src/main/
        resources/
          fabric.mod.json
          assets/downloadmod/
            icon.png
            lang/en_us.json
            textures/gui/   logo_*, discord_*  (16 to 256 px)
        java/io/downloadmod/
          DownloadModClient.java
          Config.java
          ModConfig.java
          Async.java
          api/
            ContentType.java
            Models.java
            ModrinthApi.java
          gui/
            DownloadModScreen.java
            InstallScreen.java
            VersionPickerScreen.java
            WorldPickerScreen.java
            GalleryScreen.java
            SettingsScreen.java
            RestartDialog.java
            IconButton.java
          helper/
            ExitHelper.java
          image/
            ImageCache.java
            GuiIcons.java
          install/
            Resolver.java
            PlanFile.java
            Downloader.java
            InstalledIndex.java
            Swapper.java
            PendingOps.java
            RestartManager.java
            WindowsCommandLine.java
            LauncherCommand.java
            DownloadQueue.java
          servers/
            ServerCatalog.java
          update/
            UpdateChecker.java

