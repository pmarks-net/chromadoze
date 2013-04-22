Here's how to import my project from the SVN tree, using the ADT bundle:

- File > New > Project...
- Android > Android Project from Existing Code
- Browse for SVN trunk/ directory

I ran into this transient error:
"The project cannot be built until build path errors are resolved"
I was able to fix it using "Project > Clean..."

Be sure to set Eclipse to use 4-space soft tabs:
- Window > Preferences
- Search for "space"
- Change in 3 places: General, Java, and XML.

Enable the Support Library:
- Right-click on ChromaDoze > Android Tools > Add Support Library...

Import ActionBarSherlock:
- Download from http://actionbarsherlock.com/
- File > New > Project...
- Android > Android Project from Existing Code
- Root directory: .../actionbarsherlock

Make ChromaDoze depend on ActionBarSherlock:
- Right-click on ChromaDoze -> Properties
- Android > [Library] > Add...
- Select actionbarsherlock
