"""Tonai desktop app: python app/main.py"""
import os
import webview
from service import Service

if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    svc = Service()
    webview.create_window("Tonai", os.path.join(here, "ui.html"), js_api=svc,
                          width=1180, height=760, min_size=(760, 520), background_color="#0b0b0f")
    webview.start()
