# Remnawave SDK sandbox

This folder is a small Python sandbox for inspecting the Remnawave API SDK while
the Android app remains Kotlin-first.

Use it for backend/API orientation only:

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r tools/remnawave/requirements.txt
```

The Android app does not depend on this package at runtime. User-facing
subscription import is implemented in Kotlin and accepts subscription URLs or
single proxy config links from the clipboard.
