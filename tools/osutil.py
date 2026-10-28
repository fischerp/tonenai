"""Small cross-platform helpers (macOS, Windows, Linux) for things the app does outside Python:
reveal a file, open a link, move files to the trash, play the opening sound."""
import os, shutil, subprocess, sys, webbrowser

MAC, WIN = sys.platform == "darwin", sys.platform == "win32"


def reveal(path):
    """Show a file (or folder) in the system file manager."""
    path = os.path.abspath(path)
    if MAC:
        subprocess.run(["open", "-R", path])
    elif WIN:
        # explorer returns a non-zero exit code even on success, so don't check it
        subprocess.run(["explorer", "/select," + path] if os.path.isfile(path) else ["explorer", path])
    else:
        subprocess.run(["xdg-open", path if os.path.isdir(path) else os.path.dirname(path)])


def open_url(url):
    webbrowser.open(url)


def trash(files, fallback_dir):
    """Move files to the Trash / Recycle Bin. If that is not possible, keep a recoverable copy in fallback_dir."""
    if MAC:
        items = ", ".join('POSIX file "%s"' % f for f in files)
        r = subprocess.run(["osascript", "-e", 'tell application "Finder" to delete {%s}' % items],
                           capture_output=True, text=True)
        if r.returncode == 0:
            return
    else:
        try:
            from send2trash import send2trash
            for f in files:
                send2trash(f)
            return
        except Exception:
            pass
    os.makedirs(fallback_dir, exist_ok=True)
    for f in files:
        shutil.move(f, os.path.join(fallback_dir, os.path.basename(f)))


_proc = None


def play_sound(path):
    """Play a .wav without blocking. Returns False if this platform has no simple way to do it."""
    global _proc
    if not os.path.exists(path):
        return False
    try:
        if MAC:
            _proc = subprocess.Popen(["afplay", path], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        elif WIN:
            import winsound
            winsound.PlaySound(path, winsound.SND_FILENAME | winsound.SND_ASYNC)
        else:
            for player in (["paplay"], ["aplay", "-q"], ["play", "-q"]):
                if shutil.which(player[0]):
                    _proc = subprocess.Popen(player + [path], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                    break
            else:
                return False
        return True
    except (OSError, RuntimeError):
        return False


def stop_sound():
    global _proc
    if WIN:
        try:
            import winsound
            winsound.PlaySound(None, winsound.SND_PURGE)
        except Exception:
            pass
    if _proc and _proc.poll() is None:
        _proc.terminate()
    _proc = None


def utf8_or_relaunch():
    """Windows opens text files with the legacy code page unless UTF-8 mode is on, which breaks accented
    song titles. Relaunch once in UTF-8 mode (a no-op everywhere else)."""
    if WIN and not sys.flags.utf8_mode and not os.environ.get("TONENAI_RELAUNCHED"):
        os.environ["TONENAI_RELAUNCHED"] = "1"
        sys.exit(subprocess.call([sys.executable, "-X", "utf8"] + sys.argv))
