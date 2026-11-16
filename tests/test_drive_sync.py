"""Drive sync against an in-memory Drive (no network). Run: python3 -m unittest discover -s tests"""
import hashlib, json, os, re, sys, tempfile, unittest
from pathlib import Path
from urllib.parse import parse_qs, urlparse

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app"))
import drive_sync as D


class FakeDrive:
    def __init__(self):
        self.files, self.n = {}, 0     # id -> {name, content, modifiedTime}

    def put(self, name, content, when="2026-01-01T00:00:00Z"):
        self.n += 1
        self.files[str(self.n)] = {"name": name, "content": content, "modifiedTime": when}
        return str(self.n)

    def names(self):
        return {f["name"]: f["content"] for f in self.files.values()}

    def __call__(self, method, url, data=None, headers=None):
        u = urlparse(url)
        if u.netloc == "oauth2.googleapis.com":
            return 200, json.dumps({"access_token": "t", "expires_in": 3600}).encode()
        assert headers["Authorization"] == "Bearer t"
        fid = u.path.rsplit("/", 1)[1] if re.search(r"/files/[^/]+$", u.path) else None
        if method == "GET" and not fid:
            return 200, json.dumps({"files": [{"id": i, "name": f["name"], "md5Checksum": hashlib.md5(f["content"]).hexdigest(),
                                               "modifiedTime": f["modifiedTime"], "size": str(len(f["content"]))}
                                              for i, f in self.files.items()]}).encode()
        if method == "GET":
            return 200, self.files[fid]["content"]
        if method == "DELETE":
            self.files.pop(fid, None)
            return 204, b""
        if method == "PATCH":
            self.files[fid].update(content=data, modifiedTime="2030-01-01T00:00:00Z")
            return 200, b"{}"
        if method == "POST":
            head, rest = data.split(b"\r\n\r\n", 2)[1:]
            name = json.loads(head.split(b"\r\n--")[0])["name"]
            self.put(name, rest.rsplit(b"\r\n--", 1)[0])
            return 200, b"{}"
        raise AssertionError(method + " " + url)


def song(text):
    return json.dumps({"name": text}).encode()


class DriveSyncTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.drive = FakeDrive()
        self.devices = [self.device(root / "mac"), self.device(root / "phone")]
        self.a, self.b = self.devices

    def tearDown(self):
        self.tmp.cleanup()

    def device(self, root):
        (root / "songs").mkdir(parents=True)
        (root / "data").mkdir()
        (root / "data" / "google_oauth_client.json").write_text(json.dumps({"installed": {"client_id": "c", "client_secret": "s"}}))
        store = D.SecretStore(root / "secrets.json")
        store.set(D.REFRESH_TOKEN, "r")
        sync = D.DriveSync(root / "songs", root / "data", store, self.drive, trash=lambda p: os.remove(p))
        sync.songs = root / "songs"
        return sync

    def test_new_song_travels_and_matching_files_are_left_alone(self):
        (self.a.songs / "x-one.json").write_bytes(song("one"))
        self.assertEqual(self.a.sync()["uploaded"], 1)
        self.assertEqual(self.b.sync()["downloaded"], 1)
        self.assertEqual((self.b.songs / "x-one.json").read_bytes(), song("one"))
        self.assertEqual(sum(self.a.sync().values()) + sum(self.b.sync().values()), 0)
        self.assertEqual(self.b.revision, 1)

    def test_edit_and_delete_propagate(self):
        (self.a.songs / "s.json").write_bytes(song("v1"))
        self.a.sync(); self.b.sync()
        (self.b.songs / "s.json").write_bytes(song("v2"))
        self.b.sync()
        self.assertEqual(self.a.sync()["downloaded"], 1)
        self.assertEqual((self.a.songs / "s.json").read_bytes(), song("v2"))
        os.remove(self.a.songs / "s.json")
        self.assertEqual(self.a.sync()["deleted_in_drive"], 1)
        self.assertEqual(self.b.sync()["deleted_here"], 1)
        self.assertFalse((self.b.songs / "s.json").exists())

    def test_conflict_keeps_both_copies(self):
        (self.a.songs / "s.json").write_bytes(song("v1"))
        self.a.sync(); self.b.sync()
        (self.b.songs / "s.json").write_bytes(song("phone"))
        self.b.sync()                                   # Drive copy now dated 2030: newer than the Mac's edit
        (self.a.songs / "s.json").write_bytes(song("mac"))
        self.assertEqual(self.a.sync()["conflicts"], 1)
        self.assertEqual((self.a.songs / "s.json").read_bytes(), song("phone"))
        kept = list((self.a.data_dir / "backups" / "sync-conflicts").iterdir())
        self.assertEqual([p.read_bytes() for p in kept], [song("mac")])

    def test_an_empty_side_never_deletes_the_other(self):
        (self.a.songs / "s.json").write_bytes(song("v1"))
        (self.a.songs / "t.tsl").write_bytes(song("t"))
        (self.a.songs / "u.json").write_bytes(song("u"))
        self.a.sync()
        self.drive.files.clear()                        # Drive emptied, or another Google account
        self.assertEqual(self.a.sync()["uploaded"], 3)
        for p in self.a.songs.iterdir():                # this computer's folder emptied
            os.remove(p)
        self.assertEqual(self.a.sync()["downloaded"], 3)
        self.assertEqual(len(self.drive.files), 3)

    def test_only_song_files_are_touched(self):
        self.drive.put("../evil.json", b"{}")
        self.drive.put("notes.txt", b"hi")
        self.drive.put("bad.json", b"not json")
        (self.a.songs / "covers").mkdir()
        (self.a.songs / "README.md").write_text("x")
        with self.assertRaises(D.SyncError):
            self.a.sync()                               # bad.json is refused, nothing is written
        self.assertEqual(sorted(p.name for p in self.a.songs.iterdir()), ["README.md", "covers"])
        self.assertEqual(self.a.status()["last_result"], "error")

    def test_livesets_travel_and_are_never_deleted(self):
        lists = json.dumps([{"id": "a-1", "name": "A", "slots": {}, "notes": ""}]).encode()
        (self.a.data_dir / "livesets.json").write_bytes(lists)
        self.assertEqual(self.a.sync()["uploaded"], 1)
        self.assertEqual(self.b.sync()["downloaded"], 1)
        self.assertEqual((self.b.data_dir / "livesets.json").read_bytes(), lists)
        os.remove(self.a.data_dir / "livesets.json")            # lost here: restored, not deleted in Drive
        self.assertEqual(self.a.sync()["downloaded"], 1)
        self.assertEqual((self.a.data_dir / "livesets.json").read_bytes(), lists)
        self.drive.files.clear()                                # lost in Drive: uploaded again, not deleted here
        self.a.sync(); self.b.sync()
        self.assertEqual(self.drive.names(), {"livesets.json": lists})
        self.assertEqual((self.b.data_dir / "livesets.json").read_bytes(), lists)

    def test_data_files_travel_with_their_own_format_checks(self):
        meta = json.dumps({"a-song": {"status": "learning", "notes": "slow down"}}).encode()
        guitars = json.dumps([{"id": "g", "name": "G"}]).encode()
        (self.a.data_dir / "song_meta.json").write_bytes(meta)
        (self.a.data_dir / "guitars.json").write_bytes(guitars)
        self.assertEqual(self.a.sync()["uploaded"], 2)
        self.assertEqual(self.b.sync()["downloaded"], 2)
        self.assertEqual((self.b.data_dir / "song_meta.json").read_bytes(), meta)
        self.assertEqual((self.b.data_dir / "guitars.json").read_bytes(), guitars)

    def test_data_file_names_in_the_songs_folder_are_not_songs_and_wrong_shapes_are_refused(self):
        (self.a.songs / "livesets.json").write_bytes(song("not a liveset"))
        (self.a.songs / "rig.json").write_bytes(song("not a rig"))
        self.assertEqual(self.a.sync()["uploaded"], 0)          # only the files in data/ count
        self.drive.put("livesets.json", json.dumps({"not": "a list"}).encode())
        with self.assertRaises(D.SyncError):
            self.a.sync()
        self.assertFalse((self.a.data_dir / "livesets.json").exists())
        self.drive.files.clear()
        self.drive.put("song_meta.json", json.dumps([1, 2]).encode())      # a dict file must be an object
        with self.assertRaises(D.SyncError):
            self.a.sync()

    def test_plan_table(self):
        self.assertEqual(D.plan({"a": "1", "b": "1", "c": "2", "d": "2", "e": "1"},
                                {"a": "1", "c": "1", "d": "3", "f": "1", "g": "1"},
                                {"a": "1", "b": "1", "c": "1", "d": "1", "f": "1"}),
                         {"b": "delete_local", "c": "upload", "d": "conflict", "e": "upload", "f": "delete_remote", "g": "download"})


if __name__ == "__main__":
    unittest.main()
