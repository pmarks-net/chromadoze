Assuming you already have Eclipse set up with the Android SDK, here's how
to import my project from the SVN tree:

- File > New > Android Project
- Create project from existing source
- Browse for SVN trunk/ directory
- Build Target: Android 1.6

I ran into this transient error:
"The project cannot be built until build path errors are resolved"
I was able to fix it using "Project > Clean..."
