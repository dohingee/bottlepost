"""
Revokes the Apple Development certificates that earlier CI runs created through the App Store
Connect API key ("Created via API"). Each run signs the archive on a fresh runner, so Xcode makes a
new development certificate every time, and the account's limit is soon reached ("Choose a
certificate to revoke"). Their private keys died with those runners, so nothing else uses them.
Distribution signing is cloud-managed and untouched. Env: KEY_ID, ISSUER, KEY_PATH.
"""
import base64
import json
import os
import subprocess
import time
import urllib.request

API = "https://api.appstoreconnect.apple.com/v1"


def b64(data: bytes) -> bytes:
    return base64.urlsafe_b64encode(data).rstrip(b"=")


def raw_signature(der: bytes) -> bytes:
    """ES256 JWTs need r||s; openssl prints an ASN.1 DER sequence of two integers."""
    i = 2 if der[1] < 0x80 else 2 + (der[1] & 0x7F)
    parts = []
    for _ in range(2):
        assert der[i] == 2
        length = der[i + 1]
        parts.append(der[i + 2 : i + 2 + length].lstrip(b"\0").rjust(32, b"\0"))
        i += 2 + length
    return b"".join(parts)


def token() -> str:
    now = int(time.time())
    header = {"alg": "ES256", "kid": os.environ["KEY_ID"], "typ": "JWT"}
    payload = {"iss": os.environ["ISSUER"], "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"}
    signing_input = b64(json.dumps(header).encode()) + b"." + b64(json.dumps(payload).encode())
    der = subprocess.run(["openssl", "dgst", "-sha256", "-sign", os.environ["KEY_PATH"]],
                         input=signing_input, capture_output=True, check=True, timeout=30).stdout
    return (signing_input + b"." + b64(raw_signature(der))).decode()


def call(method: str, url: str, auth: str):
    req = urllib.request.Request(url, method=method, headers={"Authorization": f"Bearer {auth}"})
    with urllib.request.urlopen(req, timeout=30) as res:
        body = res.read()
        return json.loads(body) if body else None


print("signing App Store Connect token", flush=True)
auth = token()
print("listing development certificates", flush=True)
certs = call("GET", f"{API}/certificates?filter[certificateType]=DEVELOPMENT,IOS_DEVELOPMENT&limit=200", auth)["data"]
revoked = 0
for cert in certs:
    name = cert["attributes"].get("name", "") + " " + (cert["attributes"].get("displayName") or "")
    if "Created via API" in name:
        print(f"revoking {cert['id']}", flush=True)
        call("DELETE", f"{API}/certificates/{cert['id']}", auth)
        revoked += 1
print(f"development certificates: {len(certs)}, revoked {revoked} made by earlier CI runs")
