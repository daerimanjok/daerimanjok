#!/usr/bin/env python3
"""
Feed the Pleos emulator a GPS track that the navigation app accepts.

Why: `adb emu geo fix` writes NMEA sentences with a wrong checksum on this emulator,
and the Pleos navigation app (which parses raw NMEA) rejects them, so the car never
moves on the map. This script builds $GPRMC/$GPGGA sentences with correct checksums
and sends them with `adb emu geo nmea`, one point per second.

Usage:
  tools/drive_gps.py LAT LON                         # put the car at one point
  tools/drive_gps.py FROM_LAT FROM_LON TO_LAT TO_LON [--kmh 30] [--hold 5]
                                                     # drive in a straight line, then stay at the end
"""
import argparse
import math
import os
import subprocess
import time
from datetime import datetime, timezone
from functools import reduce

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")


def checksum(body: str) -> str:
    return "%02X" % reduce(lambda acc, ch: acc ^ ord(ch), body, 0)


def dm(value: float, is_lat: bool) -> tuple[str, str]:
    hemi = ("N" if value >= 0 else "S") if is_lat else ("E" if value >= 0 else "W")
    value = abs(value)
    deg = int(value)
    minutes = (value - deg) * 60
    return (f"{deg:02d}{minutes:07.4f}" if is_lat else f"{deg:03d}{minutes:07.4f}"), hemi


def sentences(lat: float, lon: float, kmh: float, course: float) -> list[str]:
    now = datetime.now(timezone.utc)
    hhmmss, ddmmyy = now.strftime("%H%M%S"), now.strftime("%d%m%y")
    la, ns = dm(lat, True)
    lo, ew = dm(lon, False)
    knots = kmh / 1.852
    rmc = f"GPRMC,{hhmmss},A,{la},{ns},{lo},{ew},{knots:.2f},{course:.1f},{ddmmyy},,"
    gga = f"GPGGA,{hhmmss},{la},{ns},{lo},{ew},1,08,0.9,30.0,M,0.0,M,,"
    return [f"${s}*{checksum(s)}" for s in (rmc, gga)]


def send(lat: float, lon: float, kmh: float = 0.0, course: float = 0.0) -> None:
    for s in sentences(lat, lon, kmh, course):
        subprocess.run([ADB, "emu", "geo", "nmea", s], check=True, capture_output=True)


def distance_m(a: tuple[float, float], b: tuple[float, float]) -> float:
    r = 6_371_000
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp, dl = p2 - p1, math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def bearing(a: tuple[float, float], b: tuple[float, float]) -> float:
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dl = math.radians(b[1] - a[1])
    x = math.sin(dl) * math.cos(p2)
    y = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
    return (math.degrees(math.atan2(x, y)) + 360) % 360


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("coords", nargs="+", type=float)
    ap.add_argument("--kmh", type=float, default=30.0)
    ap.add_argument("--hold", type=int, default=5, help="seconds to keep sending the final point")
    args = ap.parse_args()

    if len(args.coords) == 2:
        for _ in range(max(args.hold, 1)):
            send(*args.coords)
            time.sleep(1)
        return
    if len(args.coords) != 4:
        ap.error("give LAT LON, or FROM_LAT FROM_LON TO_LAT TO_LON")

    start, end = tuple(args.coords[:2]), tuple(args.coords[2:])
    total = distance_m(start, end)
    course = bearing(start, end)
    steps = max(1, round(total / (args.kmh / 3.6)))
    print(f"driving {total:.0f} m in {steps} s at {args.kmh:.0f} km/h, course {course:.0f}°")
    for i in range(steps + 1):
        f = i / steps
        lat = start[0] + (end[0] - start[0]) * f
        lon = start[1] + (end[1] - start[1]) * f
        send(lat, lon, args.kmh if i < steps else 0.0, course)
        print(f"  {i:3d}/{steps}  {lat:.6f},{lon:.6f}  {total * (1 - f):5.0f} m to go", flush=True)
        time.sleep(1)
    for _ in range(args.hold):
        send(*end)
        time.sleep(1)


if __name__ == "__main__":
    main()
