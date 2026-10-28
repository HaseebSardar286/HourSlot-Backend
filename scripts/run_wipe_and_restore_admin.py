"""Wipe marketplace dummy data and restore the super-admin account."""
from pathlib import Path
import sys

try:
    import psycopg2
except ImportError:
    import subprocess

    subprocess.check_call([sys.executable, "-m", "pip", "install", "psycopg2-binary", "-q"])
    import psycopg2

root = Path(__file__).resolve().parents[1]
env = {}
for line in (root / ".env").read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    k, v = line.split("=", 1)
    env[k] = v.strip().strip('"')

dsn = env["DATABASE_URL"]
if dsn.startswith("jdbc:"):
    dsn = dsn[len("jdbc:") :]

conn = psycopg2.connect(dsn, user=env.get("DATABASE_USER"), password=env.get("DATABASE_PASSWORD"))
conn.autocommit = False
cur = conn.cursor()

wipe_sql = (root / "scripts" / "wipe_marketplace_data.sql").read_text(encoding="utf-8")
# strip BEGIN/COMMIT — we manage the transaction
wipe_sql = wipe_sql.replace("BEGIN;", "").replace("COMMIT;", "")
cur.execute(wipe_sql)

seed_sql = (root / "scripts" / "seed_super_admin.sql").read_text(encoding="utf-8")
cur.execute(seed_sql)

cur.execute("SELECT count(*) FROM categories")
cats = cur.fetchone()[0]
cur.execute("SELECT count(*) FROM reviews")
reviews = cur.fetchone()[0]
cur.execute("SELECT count(*) FROM businesses")
biz = cur.fetchone()[0]
cur.execute("SELECT count(*) FROM users")
users = cur.fetchone()[0]
cur.execute("SELECT email FROM users WHERE deleted_at IS NULL")
emails = [r[0] for r in cur.fetchall()]

conn.commit()
cur.close()
conn.close()

print(f"categories={cats} reviews={reviews} businesses={biz} users={users}")
print("users:", emails)
