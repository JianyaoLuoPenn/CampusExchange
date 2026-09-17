"""Black-box smoke check through the frontend/API proxy; uses only fictional mock data."""
import json
import secrets
import sys
import time
from urllib.request import Request, urlopen

base = (sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:5173').rstrip('/') + '/api/campus'

def request(path, data=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = Request(base + path, data=None if data is None else json.dumps(data).encode(), headers=headers)
    with urlopen(req, timeout=20) as response:
        return json.load(response)

config = request('/config')
assert config['paymentMode'] == 'mock', 'Smoke test only runs in explicitly labelled mock mode'
items = request('/listings')
assert items, 'Expected fictional seed listings'
item = next(p for p in items if p['status'] == 'AVAILABLE' and p['depositCents'] > 0)
assert 'pickupAddress' not in item, 'Public listings must not expose pickup addresses'
account = request('/auth/signup', {'fullName': 'Smoke Test Buyer', 'email': f'smoke-{secrets.token_hex(6)}@example.test', 'password': secrets.token_urlsafe(18)})
token = account['token']
booking = request('/reservations', {'productId': item['id'], 'pickupSlot': item['pickupSlots'][0]}, token)
assert booking['status'] == 'PENDING_PAYMENT' and booking['pickupAddress'] is None
booking = request(f"/reservations/{booking['id']}/simulate", {'success': True}, token)
assert booking['status'] == 'RESERVED' and booking['pickupAddress']
booking = request(f"/reservations/{booking['id']}/cancel", {}, token)
assert booking['status'] == 'CANCELLED' and booking['paymentState'] == 'REFUND_PENDING'
for _ in range(20):
    booking = request(f"/reservations/{booking['id']}", token=token)
    if booking['paymentState'] == 'REFUNDED':
        break
    time.sleep(1)
assert booking['paymentState'] == 'REFUNDED', 'Scheduled mock refund did not complete'
assert request(f"/listings/{item['id']}")['status'] == 'AVAILABLE'
print('PASS: frontend proxy, public privacy, signup, reservation, simulated deposit, cancellation, scheduled refund and release')
