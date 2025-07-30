#!/usr/bin/env python3
import subprocess
import base64
import paho.mqtt.client as mqtt

# CONFIG
BROKER     = "localhost"
PORT       = 1883
SUB_TOPIC  = "plant/notification"
PUB_TOPIC  = "plant/image"
IMAGE_PATH = "/home/pi/plantwise_cam/last.jpg"
CAPTURE_CMD = [
    "libcamera-still",
    "-n",              # no preview
    "-t", "1",         # timeout
    "--width", "640",  # lower resolution width
    "--height", "480", # lower resolution height
    "--quality", "60", # lower quality (0-100)
    "-o", IMAGE_PATH
]

def on_connect(client, userdata, flags, rc):
    print(f"Connected (rc={rc}). Subscribing to {SUB_TOPIC}")
    client.subscribe(SUB_TOPIC, qos=1)

def on_message(client, userdata, msg):
    text = msg.payload.decode().strip().lower()
    print(f"Received on {msg.topic}: '{text}'")
    if text == "watering_start":
        subprocess.run(CAPTURE_CMD, check=True)
        with open(IMAGE_PATH, "rb") as f:
            b64 = base64.b64encode(f.read()).decode("ascii")
        client.publish(PUB_TOPIC, b64, qos=1)
        print(f"Published image to {PUB_TOPIC} ({len(b64)} chars)")

def main():
    client = mqtt.Client("PlantCamBridge")
    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(BROKER, PORT, keepalive=60)
    client.loop_forever()

if __name__ == "__main__":
    main()
