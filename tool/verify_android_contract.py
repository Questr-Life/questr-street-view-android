"""Guard the Android panorama callback and one-view lifecycle contract."""

from pathlib import Path


source = (
    Path(__file__).resolve().parents[1]
    / "android/src/main/kotlin/com/questrlife/questr_street_view_android/"
    "QuestrStreetViewAndroidPlugin.kt"
).read_text(encoding="utf-8")

required = (
    "location: StreetViewPanoramaLocation? ->",
    'report(if (location == null) "zero_results" else "available")',
    "releaseView()\n        val params = args as? Map<*, *>",
    "handler.removeCallbacks(timeout)",
)
missing = [item for item in required if item not in source]
if missing:
    raise SystemExit(f"Street View contract regression: {missing}")
print("PASS: nullable no-coverage callback and single-view lifecycle preserved")
