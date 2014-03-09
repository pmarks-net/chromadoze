Here's how to import my project from the SVN tree, using the ADT bundle:

- File > New > Project...
- Android > Android Project from Existing Code
- Browse for SVN trunk/ directory

Enable the Support Libraries:

- File > Import...
- Existing Android Code Into Workspace
- Browse to: adt-bundle-.../sdk/extras/android/support/v7/appcompat
- Finish

- Under the new android-support-v7-appcompat project:
  + expand libs/
    - Right-click android-support-v4.jar
      + Build Path > Add to Build Path
    - Right-click android-support-v7-appcompat.jar
      + Build Path > Add to Build Path
  + Right-click project
  + Build Path > Configure Build Path
    - Check android-support-v7-appcompat.jar
    - Check android-support-v4.jar
    - Uncheck Android Dependencies

- Right-click ChromaDoze > Properties
- Android > Library > Add...
- Select android-support-v7-appcompat, OK.

Optional: enable Proguard, to build a smaller .apk
- Edit project.properties, and uncomment proguard.config
- Note that my proguard-project.txt disables obfuscation.

Be sure to set Eclipse to use 4-space soft tabs:
- Window > Preferences
- Search for "space"
- Change in 3 places: General, Java, and XML.
