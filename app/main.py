"""TonenAI desktop app: python app/main.py"""
import os
import sys
import webview
from service import Service

HERE = os.path.dirname(os.path.abspath(__file__))
ICON = os.path.join(os.path.dirname(HERE), "assets", "tonenai.icns")


def brand_macos_app(name):
    """The launcher hands off to Python, so macOS would show Python's name and rocket icon.
    Rename the running app before the GUI starts; the Dock icon is set by webview.start(icon=...)."""
    if sys.platform != "darwin":
        return
    try:
        from Foundation import NSBundle
        info = NSBundle.mainBundle().infoDictionary()
        info["CFBundleName"] = name
        info["CFBundleDisplayName"] = name
    except Exception:
        pass  # cosmetic only; never block the app


if __name__ == "__main__":
    brand_macos_app("TonenAI")
    svc = Service()
    webview.create_window("TonenAI", os.path.join(HERE, "ui.html"), js_api=svc,
                          width=1180, height=760, min_size=(760, 520), background_color="#0b0b0f")
    webview.start(icon=ICON if os.path.isfile(ICON) else None)
