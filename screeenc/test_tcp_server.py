#!/usr/bin/env python3
"""
Simple TCP Server to test Android Video Receiver
Sends dummy H.264 NAL units to simulate video streaming
"""

import socket
import time
import sys

HOST = '127.0.0.1'
PORT = 27183

# Simple H.264 NAL units for testing (SPS, PPS, and I-frame headers)
# These are minimal valid H.264 NAL units that won't cause decoder errors
NAL_START_CODE = b'\x00\x00\x00\x01'

# Sample SPS (Sequence Parameter Set)
SPS_NAL = NAL_START_CODE + b'\x67\x42\xc0\x1e\xd9\x00\xf0\x04\x4f\xcb\x80\xb5\x01\x01\x01\x40'

# Sample PPS (Picture Parameter Set)
PPS_NAL = NAL_START_CODE + b'\x68\xce\x3c\x80'

# Sample IDR frame header (I-frame)
IDR_NAL = NAL_START_CODE + b'\x65\x88\x84\x00\x33\xff'

def send_test_stream(client_socket):
    """Send a test H.264 stream"""
    print("Sending test H.264 stream...")
    frame_count = 0
    
    try:
        # Send SPS and PPS first
        client_socket.sendall(SPS_NAL)
        time.sleep(0.01)
        client_socket.sendall(PPS_NAL)
        time.sleep(0.01)
        
        # Send frames at ~30 FPS
        while True:
            # Send IDR frame every 30 frames
            if frame_count % 30 == 0:
                client_socket.sendall(IDR_NAL)
                print(f"Sent frame {frame_count} (I-frame)")
            else:
                # Send P-frame (simplified)
                p_frame = NAL_START_CODE + b'\x41\x9a\x21\x8c\x48'
                client_socket.sendall(p_frame)
                if frame_count % 10 == 0:
                    print(f"Sent frame {frame_count}")
            
            frame_count += 1
            time.sleep(1/30)  # 30 FPS
            
    except BrokenPipeError:
        print("\nClient disconnected")
    except KeyboardInterrupt:
        print("\nStopping stream...")

def main():
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    try:
        server_socket.bind((HOST, PORT))
        server_socket.listen(1)
        print(f"✓ TCP Server listening on {HOST}:{PORT}")
        print("\nWaiting for Android device to connect...")
        print("Make sure to run: adb reverse tcp:27183 tcp:27183")
        print("\nPress Ctrl+C to stop\n")
        
        while True:
            client_socket, address = server_socket.accept()
            print(f"\n✓ Connected from {address}")
            
            try:
                send_test_stream(client_socket)
            except Exception as e:
                print(f"Error: {e}")
            finally:
                client_socket.close()
                print("Connection closed\n")
                print("Waiting for next connection...")
                
    except KeyboardInterrupt:
        print("\n\nShutting down server...")
    except Exception as e:
        print(f"Server error: {e}")
    finally:
        server_socket.close()
        print("Server stopped")

if __name__ == "__main__":
    main()
