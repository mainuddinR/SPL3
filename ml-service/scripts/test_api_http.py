import os
import subprocess
import time
import requests
import json

env = os.environ.copy()
env["CLASSIFIER_TYPE"] = "codebert"
env["MODEL_PATH"] = r"D:\8th semester\SPL3\ml-service\models\codebert_satd\best_model"
env["PYTHONPATH"] = r"D:\8th semester\SPL3\ml-service"

# Start FastAPI server
server = subprocess.Popen(
    [r"D:\8th semester\SPL3\ml-service\venv\Scripts\uvicorn.exe", "app.main:app", "--port", "8001"],
    env=env,
    cwd=r"D:\8th semester\SPL3\ml-service"
)

# Wait for server to start
print("Waiting for server to start...")
time.sleep(20)  # Wait for model to load into memory

try:
    print("Sending SATD request...")
    satd_payload = {
        "comment": "TODO: fix this temporary workaround later",
        "preceding_code": "public void process() {",
        "succeeding_code": "return; }"
    }
    r_satd = requests.post("http://localhost:8001/api/v1/satd-detect", json=satd_payload)
    print(f"SATD Status: {r_satd.status_code}")
    print(f"SATD Response: {json.dumps(r_satd.json(), indent=2)}")

    print("\nSending NON-SATD request...")
    non_satd_payload = {
        "comment": "Initialize the user service",
        "preceding_code": "public class UserService {",
        "succeeding_code": "private String name;"
    }
    r_non = requests.post("http://localhost:8001/api/v1/satd-detect", json=non_satd_payload)
    print(f"NON-SATD Status: {r_non.status_code}")
    print(f"NON-SATD Response: {json.dumps(r_non.json(), indent=2)}")

finally:
    server.terminate()
    server.wait()
    print("Server terminated.")
