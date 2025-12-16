# Curvature
## What the heck is this?
Curvature is a Paper Minecraft server plugin that tracks per-player “curve” values (these could be metrics such as playtime, movement, kills, etc.), applies configurable mathematical curves (logistic, exponential, polynomial, optionally bounded), and exposes the resulting multipliers via placeholders (i.e. `%curvature_<curve>_multiplier%`). It persists player X-values in SQLite, decays them over time, and offers a public CurvatureService so other plugins can read X/multipliers programmatically.
