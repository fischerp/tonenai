"""Song requests from the phone. Run: python3 -m unittest discover -s tests"""
import json, os, sys, tempfile, time, unittest
from pathlib import Path

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app"))
import phone_requests as R
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from test_drive_sync import DriveSyncTest as Base, song     # noqa: E402,F401

RID = "0123456789ab"


def req(text="Under the Bridge, Red Hot Chili Peppers", rid=RID, created=None):
    return json.dumps({"id": rid, "text": text, "created": created or int(time.time() * 1000)}).encode()


class PhoneRequestsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.inbox = R.Inbox(Path(self.tmp.name))

    def tearDown(self):
        self.tmp.cleanup()

    def test_text_is_one_short_plain_line(self):
        self.assertEqual(R.clean_text("  Under   the Bridge "), "Under the Bridge")
        for bad in ["", "   ", "x" * 201, "line\x00break", None, 5]:
            self.assertIsNone(R.clean_text(bad))
        self.assertEqual(R.clean_text("a\nb"), "a b")          # newlines are folded to spaces, never passed on

    def test_check_rejects_wrong_names_and_shapes(self):
        R.check(f"req-{RID}.json", json.loads(req()))
        for name, data in [("req-xyz.json", json.loads(req())), (f"req-{RID}.json", {"id": "ffffffffffff", "text": "a", "created": 1}),
                           (f"req-{RID}.json", {"id": RID, "text": "a\nb", "created": 1}), (f"res-{RID}.json", {"id": RID, "status": "nope", "updated": 1}),
                           (f"res-{RID}.json", {"id": RID, "status": "done", "updated": 1, "song": "../x"})]:
            with self.assertRaises(ValueError):
                R.check(name, data)

    def test_items_merge_results_and_ignore_bad_files(self):
        (Path(self.tmp.name) / f"req-{RID}.json").write_bytes(req())
        (Path(self.tmp.name) / "req-aaaaaaaaaaaa.json").write_text("{not json")
        self.assertEqual([i["status"] for i in self.inbox.items()], ["new"])
        self.inbox.write_result(RID, "done", "ok", song="x-y")
        item = self.inbox.items()[0]
        self.assertEqual((item["status"], item["song"]), ("done", "x-y"))
        self.assertEqual(self.inbox.made_today(), 1)

    def test_cleanup_removes_old_final_requests_only(self):
        old = int((time.time() - 8 * 86400) * 1000)
        (Path(self.tmp.name) / f"req-{RID}.json").write_bytes(req(created=old))
        self.inbox.write_result(RID, "done", now=time.time() - 8 * 86400)
        (Path(self.tmp.name) / "req-bbbbbbbbbbbb.json").write_bytes(req(rid="bbbbbbbbbbbb", created=old))   # old but never handled: kept until it expires
        self.assertEqual(self.inbox.cleanup(), 2)
        self.assertEqual([i["id"] for i in self.inbox.items()], ["bbbbbbbbbbbb"])


class RequestSyncTest(Base):
    """Requests travel phone -> computer and results computer -> phone through Drive."""

    def test_request_and_result_travel_and_bad_files_do_not_block(self):
        mac, phone = self.a, self.b
        d = phone.data_dir / "requests"
        d.mkdir()
        (d / f"req-{RID}.json").write_bytes(req())
        phone.sync()
        self.drive.put("req-ffffffffffff.json", b'{"id":"ffffffffffff","text":"a\\nb","created":1}')     # malformed: must be skipped
        mac.sync()
        self.assertTrue((mac.data_dir / "requests" / f"req-{RID}.json").is_file())
        self.assertFalse((mac.data_dir / "requests" / "req-ffffffffffff.json").exists())
        R.Inbox(mac.data_dir / "requests").write_result(RID, "done", "ok", song="x-y")
        mac.sync()
        phone.sync()
        self.assertEqual(R.Inbox(d).items()[0]["status"], "done")
        # the computer removes it after a week; the phone follows through the sync
        for name in (f"req-{RID}.json", f"res-{RID}.json"):
            (mac.data_dir / "requests" / name).unlink()
        mac.sync()
        phone.sync()
        self.assertFalse((d / f"req-{RID}.json").exists())

    def test_request_names_are_not_songs(self):
        (self.a.songs / f"req-{RID}.json").write_bytes(song("x"))      # same shape as a request name, but in the songs folder
        self.assertEqual(self.a.sync()["uploaded"], 0)


if __name__ == "__main__":
    unittest.main()
