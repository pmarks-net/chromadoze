ChromaDoze is an Android app that dynamically generates white/colored noise according to a frequency/amplitude spectrum sketched by the user.

It is intended to be used as a sleep sound generator.  It provides rapid feedback to adjustments in the spectrum, and is designed to minimize CPU usage in the steady state.

It works by running shaped white noise through an Inverse Discrete Cosine Transform, generating a few megabytes of distinct audio blocks.  The steady-state behavior selects blocks at random, and smoothly crossfades between them.

Here's its [page on Google Play](https://play.google.com/store/apps/details?id=net.pmarks.chromadoze).

Screenshots:

<img src='misc/screenshot1.png?raw=true' width='360px'>
<img src='misc/screenshot2.png?raw=true' width='360px'>
<img src='misc/screenshot3.png?raw=true' width='360px'>
